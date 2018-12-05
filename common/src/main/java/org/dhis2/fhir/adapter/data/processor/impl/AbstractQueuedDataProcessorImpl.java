package org.dhis2.fhir.adapter.data.processor.impl;

/*
 * Copyright (c) 2004-2018, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.dhis2.fhir.adapter.data.model.DataGroup;
import org.dhis2.fhir.adapter.data.model.DataGroupId;
import org.dhis2.fhir.adapter.data.model.DataGroupUpdate;
import org.dhis2.fhir.adapter.data.model.ProcessedItem;
import org.dhis2.fhir.adapter.data.model.ProcessedItemId;
import org.dhis2.fhir.adapter.data.model.ProcessedItemInfo;
import org.dhis2.fhir.adapter.data.model.QueuedItemId;
import org.dhis2.fhir.adapter.data.processor.DataItemQueueItem;
import org.dhis2.fhir.adapter.data.processor.DataProcessorItemRetriever;
import org.dhis2.fhir.adapter.data.processor.QueuedDataProcessor;
import org.dhis2.fhir.adapter.data.repository.AlreadyQueuedException;
import org.dhis2.fhir.adapter.data.repository.DataGroupUpdateRepository;
import org.dhis2.fhir.adapter.data.repository.IgnoredQueuedItemException;
import org.dhis2.fhir.adapter.data.repository.ProcessedItemRepository;
import org.dhis2.fhir.adapter.data.repository.QueuedItemRepository;
import org.dhis2.fhir.adapter.security.SystemAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Abstract implementation of {@link QueuedDataProcessor}.
 *
 * @param <P>  the concrete type of the processed item.
 * @param <PI> the concrete type of the ID of the processed item.
 * @param <QG> the concrete type of the queued item that is used for queuing the data group.
 * @param <QI> the concrete type of the queued item that is used for queuing the item.
 * @param <G>  the concrete type of the group of the ID that is constant for a specific use case.
 * @param <GI> the concrete type of the group ID of the group <code>G</code>.
 * @author volsch
 */
public abstract class AbstractQueuedDataProcessorImpl<P extends ProcessedItem<PI, G>, PI extends ProcessedItemId<G>, QG extends QueuedItemId<G>, QI extends QueuedItemId<G>, G extends DataGroup, GI extends DataGroupId> implements QueuedDataProcessor<G>
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    private final QueuedItemRepository<QG, G> queuedGroupRepository;

    private final JmsTemplate groupQueueJmsTemplate;

    private final DataGroupUpdateRepository<DataGroupUpdate<G>, G> dataGroupUpdateRepository;

    private final ProcessedItemRepository<P, PI, G> processedItemRepository;

    private final QueuedItemRepository<QI, G> queuedItemRepository;

    private final JmsTemplate itemQueueJmsTemplate;

    private final PlatformTransactionManager platformTransactionManager;

    private final SystemAuthenticationToken systemAuthenticationToken;

    public AbstractQueuedDataProcessorImpl(
        @Nonnull QueuedItemRepository<QG, G> queuedGroupRepository,
        @Nonnull JmsTemplate groupQueueJmsTemplate,
        @Nonnull DataGroupUpdateRepository<DataGroupUpdate<G>, G> dataGroupUpdateRepository,
        @Nonnull ProcessedItemRepository<P, PI, G> processedItemRepository,
        @Nonnull QueuedItemRepository<QI, G> queuedItemRepository,
        @Nonnull JmsTemplate itemQueueJmsTemplate,
        @Nonnull PlatformTransactionManager platformTransactionManager,
        @Nonnull SystemAuthenticationToken systemAuthenticationToken )
    {
        this.queuedGroupRepository = queuedGroupRepository;
        this.groupQueueJmsTemplate = groupQueueJmsTemplate;
        this.dataGroupUpdateRepository = dataGroupUpdateRepository;
        this.processedItemRepository = processedItemRepository;
        this.queuedItemRepository = queuedItemRepository;
        this.itemQueueJmsTemplate = itemQueueJmsTemplate;
        this.platformTransactionManager = platformTransactionManager;
        this.systemAuthenticationToken = systemAuthenticationToken;
    }

    @HystrixCommand
    @Override
    public void process( @Nonnull G group )
    {
        final TransactionStatus transactionStatus = platformTransactionManager.getTransaction( new DefaultTransactionDefinition() );
        try
        {
            logger.debug( "Checking for a queued entry of group {}.", group.getGroupId() );
            try
            {
                queuedGroupRepository.enqueue( createQueuedGroupId( group ) );
            }
            catch ( AlreadyQueuedException e )
            {
                logger.debug( "There is already a queued entry for group {}.", group.getGroupId() );
                return;
            }
            catch ( IgnoredQueuedItemException e )
            {
                // has already been logger with sufficient details
                return;
            }

            logger.debug( "Enqueuing entry for group {}.", group.getGroupId() );
            groupQueueJmsTemplate.convertAndSend( createDataGroupQueueItem( group ), message -> {
                // only one message for a single group must be processed at a specific time (grouping)
                message.setStringProperty( "JMSXGroupID", group.getGroupId().toString() );
                return message;
            } );
            logger.info( "Enqueued entry for group {}.", group.getGroupId() );
        }
        finally
        {
            finalizeTransaction( transactionStatus );
        }
    }

    protected void receive( @Nonnull DataGroupQueueItem<GI> dataGroupQueueItem )
    {
        SecurityContextHolder.getContext().setAuthentication( createAuthentication() );
        try
        {
            receiveAuthenticated( dataGroupQueueItem );
        }
        finally
        {
            SecurityContextHolder.clearContext();
        }
    }

    protected void receiveAuthenticated( @Nonnull DataGroupQueueItem<GI> dataGroupQueueItem )
    {
        logger.info( "Processing queued group {}.", dataGroupQueueItem.getDataGroupId() );
        final G group = findGroupByGroupId( dataGroupQueueItem.getDataGroupId() );
        if ( group == null )
        {
            logger.warn( "Group {} is no longer available. Skipping processing of updated group.",
                dataGroupQueueItem.getDataGroupId() );
            return;
        }

        try
        {
            queuedGroupRepository.dequeued( createQueuedGroupId( group ) );
        }
        catch ( IgnoredQueuedItemException e )
        {
            // has already been logger with sufficient details
            return;
        }

        final DataProcessorItemRetriever<G> itemRetriever = getDataProcessorItemRetriever( group );
        final Instant origLastUpdated = dataGroupUpdateRepository.getLastUpdated( group );
        final AtomicLong count = new AtomicLong();
        final Instant lastUpdated = itemRetriever.poll( group, origLastUpdated, getMaxSearchCount(), items -> {
            final Instant processedAt = Instant.now();
            final Set<String> processedIds = processedItemRepository.find( group,
                items.stream().map( sr -> sr.toIdString( processedAt ) ).collect( Collectors.toList() ) );

            items.forEach( item -> {
                final String processedId = item.toIdString( processedAt );
                if ( !processedIds.contains( processedId ) )
                {
                    // persist processed item
                    processedItemRepository.process( createProcessedItem( group, processedId, processedAt ), p -> {
                        final TransactionStatus transactionStatus = platformTransactionManager.getTransaction(
                            new DefaultTransactionDefinition( TransactionDefinition.PROPAGATION_NOT_SUPPORTED ) );
                        try
                        {
                            queuedItemRepository.enqueue( createQueuedItemId( group, item ) );
                            itemQueueJmsTemplate.convertAndSend( createDataItemQueueItem( group, item ) );
                            logger.debug( "Item {} of group {} has been enqueued.", item.getId(), group.getGroupId() );
                            count.incrementAndGet();
                        }
                        catch ( AlreadyQueuedException e )
                        {
                            logger.debug( "Item {} of group {} is still queued.", item.getId(), group.getGroupId() );
                        }
                        catch ( IgnoredQueuedItemException e )
                        {
                            // has already been logger with sufficient details
                        }
                        finally
                        {
                            finalizeTransaction( transactionStatus );
                        }
                    } );
                }
            } );
        } );
        dataGroupUpdateRepository.updateLastUpdated( group, lastUpdated );

        // Purging old data must not be done before and also must not be done asynchronously. The ast updated
        // timestamp may be older than the purged data. And before purging the old data, the last updated
        // timestamp of the group must be updated by processing the complete items that belong to the group.
        purgeOldestProcessed( group );
        logger.info( "Processed queued group {} with {} enqueued items.",
            dataGroupQueueItem.getDataGroupId(), count.longValue() );
    }

    protected void purgeOldestProcessed( @Nonnull G group )
    {
        final Instant from = Instant.now().minus( getMaxProcessedAgeMinutes(), ChronoUnit.MINUTES );
        logger.debug( "Purging oldest processed items before {} for group {}.", from, group.getGroupId() );
        final int count = processedItemRepository.deleteOldest( group, from );
        logger.debug( "Purged {} oldest processed items before {} for group {}.", count, from, group.getGroupId() );
    }

    @Nonnull
    protected Authentication createAuthentication()
    {
        return systemAuthenticationToken;
    }

    protected abstract QG createQueuedGroupId( @Nonnull G group );

    @Nonnull
    protected abstract DataGroupQueueItem<GI> createDataGroupQueueItem( @Nonnull G group );

    @Nullable
    protected abstract G findGroupByGroupId( @Nonnull GI groupId );

    protected abstract int getMaxProcessedAgeMinutes();

    protected abstract int getMaxSearchCount();

    @Nonnull
    protected abstract DataProcessorItemRetriever<G> getDataProcessorItemRetriever( @Nonnull G group );

    @Nonnull
    protected abstract P createProcessedItem( @Nonnull G group, @Nonnull String id, @Nonnull Instant processedAt );

    @Nonnull
    protected abstract QI createQueuedItemId( @Nonnull G group, @Nonnull ProcessedItemInfo processedItemInfo );

    @Nonnull
    protected abstract DataItemQueueItem<GI> createDataItemQueueItem( @Nonnull G group, @Nonnull ProcessedItemInfo processedItemInfo );

    private void finalizeTransaction( @Nonnull TransactionStatus transactionStatus )
    {
        if ( transactionStatus.isRollbackOnly() )
        {
            platformTransactionManager.rollback( transactionStatus );
        }
        else
        {
            platformTransactionManager.commit( transactionStatus );
        }
    }
}
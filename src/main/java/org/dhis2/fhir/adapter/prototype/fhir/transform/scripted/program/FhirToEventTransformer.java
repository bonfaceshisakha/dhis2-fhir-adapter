package org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.program;

/*
 *  Copyright (c) 2004-2018, University of Oslo
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *  Redistributions of source code must retain the above copyright notice, this
 *  list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *  this list of conditions and the following disclaimer in the documentation
 *  and/or other materials provided with the distribution.
 *  Neither the name of the HISP project nor the names of its contributors may
 *  be used to endorse or promote products derived from this software without
 *  specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

import org.dhis2.fhir.adapter.prototype.converter.ConversionException;
import org.dhis2.fhir.adapter.prototype.dhis.converter.DhisValueConverter;
import org.dhis2.fhir.adapter.prototype.dhis.model.DhisResourceType;
import org.dhis2.fhir.adapter.prototype.dhis.model.ValueType;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.Enrollment;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.EnrollmentService;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.EnrollmentStatus;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.Event;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.EventService;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.EventStatus;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.ImmutableProgram;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.Program;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.ProgramMetadataService;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.program.ProgramStage;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.trackedentity.ImmutableTrackedEntityType;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.trackedentity.TrackedEntityInstance;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.trackedentity.TrackedEntityMetadataService;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.trackedentity.TrackedEntityService;
import org.dhis2.fhir.adapter.prototype.dhis.tracker.trackedentity.TrackedEntityType;
import org.dhis2.fhir.adapter.prototype.fhir.metadata.model.FhirResourceMapping;
import org.dhis2.fhir.adapter.prototype.fhir.metadata.model.FhirToEventMapping;
import org.dhis2.fhir.adapter.prototype.fhir.transform.FhirToDhisTransformOutcome;
import org.dhis2.fhir.adapter.prototype.fhir.transform.FhirToDhisTransformerContext;
import org.dhis2.fhir.adapter.prototype.fhir.transform.TransformException;
import org.dhis2.fhir.adapter.prototype.fhir.transform.TransformFatalException;
import org.dhis2.fhir.adapter.prototype.fhir.transform.TransformMappingException;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.AbstractFhirToDhisTransformer;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.TransformScriptException;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.TransformerScriptConstants;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.trackedentity.ImmutableScriptedTrackedEntityInstance;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.trackedentity.ScriptedTrackedEntityInstance;
import org.dhis2.fhir.adapter.prototype.fhir.transform.scripted.trackedentity.WritableScriptedTrackedEntityInstance;
import org.hl7.fhir.instance.model.api.IAnyResource;
import org.springframework.scripting.ScriptCompilationException;
import org.springframework.scripting.ScriptEvaluator;
import org.springframework.scripting.support.StaticScriptSource;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.PersistenceContext;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Component
public class FhirToEventTransformer extends AbstractFhirToDhisTransformer<Event, FhirToEventMapping>
{
    @PersistenceContext
    private EntityManager entityManager;

    private final TrackedEntityMetadataService trackedEntityMetadataService;

    private final ProgramMetadataService programMetadataService;

    private final TrackedEntityService trackedEntityService;

    private final EnrollmentService enrollmentService;

    private final EventService eventService;

    private final DhisValueConverter dhisValueConverter;

    public FhirToEventTransformer( @Nonnull ScriptEvaluator scriptEvaluator, @Nonnull EntityManager entityManager,
        @Nonnull ProgramMetadataService programMetadataService, @Nonnull TrackedEntityMetadataService trackedEntityMetadataService,
        @Nonnull TrackedEntityService trackedEntityService, @Nonnull EnrollmentService enrollmentService, @Nonnull EventService eventService,
        @Nonnull DhisValueConverter dhisValueConverter )
    {
        super( scriptEvaluator );
        this.programMetadataService = programMetadataService;
        this.trackedEntityService = trackedEntityService;
        this.trackedEntityMetadataService = trackedEntityMetadataService;
        this.enrollmentService = enrollmentService;
        this.eventService = eventService;
        this.dhisValueConverter = dhisValueConverter;
    }

    @Nonnull
    @Override
    public DhisResourceType getDhisResourceType()
    {
        return DhisResourceType.EVENT;
    }

    @Nonnull
    @Override
    public Class<Event> getDhisResourceClass()
    {
        return Event.class;
    }

    @Nonnull
    @Override
    public Class<FhirToEventMapping> getMappingClass()
    {
        return FhirToEventMapping.class;
    }

    @Override
    public boolean addScriptArguments( @Nonnull Map<String, Object> arguments, @Nonnull FhirToDhisTransformerContext context, @Nonnull FhirToEventMapping mapping ) throws TransformException
    {
        final FhirResourceMapping fhirResourceMapping = getFhirResourceMapping( mapping );

        final Program program = programMetadataService.getProgramByName( mapping.getProgramName() ).map( ImmutableProgram::new )
            .orElseThrow( () -> new TransformMappingException( "Mapping " + mapping + " requires program \"" + mapping.getProgramName() + "\" that does not exist." ) );
        arguments.put( TransformerScriptConstants.PROGRAM_ATTR_NAME, program );

        final TrackedEntityType trackedEntityType = trackedEntityMetadataService.getTypeById( program.getTrackedEntityTypeId() )
            .orElseThrow( () -> new TransformMappingException( "Program " + program.getId() + " references type " + program.getTrackedEntityTypeId() + " that does not exist." ) );
        arguments.put( TransformerScriptConstants.TRACKED_ENTITY_TYPE_ATTR_NAME, new ImmutableTrackedEntityType( trackedEntityType ) );

        final TrackedEntityInstance trackedEntityInstance = getTrackedEntityInstance( fhirResourceMapping, arguments );
        if ( trackedEntityInstance == null )
        {
            // without a tracked entity instance transformation cannot continue
            return false;
        }
        if ( !trackedEntityInstance.getTypeId().equals( program.getTrackedEntityTypeId() ) )
        {
            // referenced tracked entity instance type must match type that is required by program
            return false;
        }
        arguments.put( TransformerScriptConstants.TRACKED_ENTITY_INSTANCE_ATTR_NAME,
            new ImmutableScriptedTrackedEntityInstance( new WritableScriptedTrackedEntityInstance( trackedEntityType, trackedEntityInstance, dhisValueConverter ) ) );

        final ProgramStage programStage =
            program.getOptionalStageByName( mapping.getProgramStageName() ).orElseThrow( () -> new TransformMappingException( "Mapping " + mapping + " requires program stage \"" +
                mapping.getProgramStageName() + "\" that is not included in program \"" + mapping.getProgramName() + "\"." ) );
        arguments.put( TransformerScriptConstants.PROGRAM_STAGE_ATTR_NAME, programStage );

        // is applicable for further processing
        return true;
    }

    @Nullable
    @Override
    public FhirToDhisTransformOutcome<Event> transform( @Nonnull FhirToDhisTransformerContext context, @Nonnull IAnyResource input,
        @Nonnull FhirToEventMapping mapping, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        final Map<String, Object> arguments = new HashMap<>( scriptArguments );

        final Optional<Event> event = getResource( context, mapping, scriptArguments );
        if ( !event.isPresent() )
        {
            return null;
        }
        // if mapping does not allow automatic creation of events of this type, transformation cannot be performed
        if ( !mapping.isGenerateEvent() && event.get().isNewResource() )
        {
            return null;
        }

        final ProgramStage programStage = getProgramStage( scriptArguments );
        arguments.put( TransformerScriptConstants.ENROLLMENT_ATTR_NAME,
            new ImmutableScriptedEnrollment( new WritableScriptedEnrollment( event.get().getEnrollment(), dhisValueConverter ) ) );

        final WritableScriptedEvent scriptedEvent = new WritableScriptedEvent( programStage, event.get(), dhisValueConverter );
        arguments.put( TransformerScriptConstants.OUTPUT_ATTR_NAME, scriptedEvent );

        if ( !transform( mapping, arguments ) )
        {
            return null;
        }
        scriptedEvent.validate();

        // as soon as data values have been entered an event date must be set
        if ( scriptedEvent.isAnyDataValueModified() && (scriptedEvent.getEventDate() == null) )
        {
            event.get().setStatus( EventStatus.ACTIVE );
            event.get().setEventDate( getEventDate( getFhirResourceMapping( mapping ), event.get().getEnrollment(), arguments ) );
            event.get().setModified( true );
        }

        return new FhirToDhisTransformOutcome<>( event.get().getId(), event.get() );
    }

    @Nonnull
    @Override
    protected Optional<Event> getResourceById( @Nullable String id ) throws TransformException
    {
        // since only immunizations are supported at the moment, resolving resources by ID is not supported
        return Optional.empty();
    }

    @Nonnull
    @Override
    protected Optional<Event> getResourceByIdentifier(
        @Nonnull FhirToDhisTransformerContext context, @Nonnull FhirToEventMapping mapping, @Nullable String identifier, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        // since only immunizations are supported at the moment, resolving resources by ID is not supported
        return Optional.empty();
    }

    @Nonnull
    @Override
    protected Optional<Event> getActiveResource( @Nonnull FhirToDhisTransformerContext context, @Nonnull FhirToEventMapping mapping, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        return getEventInfo( FhirToEventTransformer::isEditableEvent, scriptArguments ).getEvent();
    }

    @Nullable
    @Override
    protected Event createResource(
        @Nonnull FhirToDhisTransformerContext context, @Nonnull FhirToEventMapping mapping, @Nullable String id, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        final EventInfo eventInfo = getEventInfo( e -> e.getStatus() != EventStatus.COMPLETED, scriptArguments );

        // repeated events are not supported currently
        if ( eventInfo.getEvent().isPresent() || eventInfo.isCompletedEvents() )
        {
            return null;
        }

        final FhirResourceMapping fhirResourceMapping = getFhirResourceMapping( mapping );
        Enrollment enrollment = eventInfo.getEnrollment().orElse( null );
        if ( enrollment == null )
        {
            if ( mapping.getAutomatedEnrollment() == null )
            {
                return null;
            }

            if ( !transform( mapping.getAutomatedEnrollment().getApplicableScript(), scriptArguments ) )
            {
                return null;
            }

            enrollment = new Enrollment( true );
            enrollment.setProgramId( eventInfo.getProgram().getId() );
            enrollment.setTrackedEntityInstanceId( eventInfo.getTrackedEntityInstance().getId() );
            enrollment.setStatus( EnrollmentStatus.ACTIVE );

            final WritableScriptedEnrollment scriptedEnrollment = new WritableScriptedEnrollment( enrollment, dhisValueConverter );
            final Map<String, Object> arguments = new HashMap<>( scriptArguments );
            arguments.put( TransformerScriptConstants.OUTPUT_ATTR_NAME, scriptedEnrollment );
            if ( !transform( mapping.getAutomatedEnrollment().getTransformScript(), arguments ) )
            {
                return null;
            }
            scriptedEnrollment.validate();
        }

        final Event event = new Event( true );
        event.setEnrollment( enrollment );
        event.setOrgUnitId( getEnrolledOrgUnitId( fhirResourceMapping, enrollment, scriptArguments ) );
        event.setProgramId( eventInfo.getProgram().getId() );
        event.setProgramStageId( eventInfo.getProgramStage().getId() );
        event.setTrackedEntityInstanceId( eventInfo.getTrackedEntityInstance().getId() );
        event.setStatus( EventStatus.ACTIVE );
        event.setEventDate( getEventDate( fhirResourceMapping, enrollment, scriptArguments ) );
        return event;
    }

    protected FhirResourceMapping getFhirResourceMapping( @Nonnull FhirToEventMapping mapping )
    {
        FhirResourceMapping fhirResourceMapping;
        try
        {
            fhirResourceMapping = entityManager.createNamedQuery( FhirResourceMapping.BY_FHIR_RESOURCE_QUERY_NAME, FhirResourceMapping.class )
                .setParameter( "fhirResourceType", mapping.getFhirResourceType() ).getSingleResult();
        }
        catch ( NoResultException e )
        {
            throw new TransformFatalException( "No FHIR resource mapping has been defined for " + mapping.getFhirResourceType() + "." );
        }
        return fhirResourceMapping;
    }

    @Nonnull
    protected TrackedEntityType getTrackedEntityType( @Nonnull Map<String, Object> scriptArguments ) throws TransformFatalException
    {
        final TrackedEntityType trackedEntityType = (TrackedEntityType) scriptArguments.get( TransformerScriptConstants.TRACKED_ENTITY_TYPE_ATTR_NAME );
        if ( trackedEntityType == null )
        {
            throw new TransformFatalException( "Tracked entity type is not included as script argument." );
        }
        return trackedEntityType;
    }

    @Nullable
    protected TrackedEntityInstance getTrackedEntityInstance( @Nonnull FhirResourceMapping resourceMapping, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        try
        {
            final Object result = getScriptEvaluator().evaluate( new StaticScriptSource(
                resourceMapping.getTrackedEntityInstanceLookupScript() ), new HashMap<>( scriptArguments ) );
            if ( (result != null) && !(result instanceof TrackedEntityInstance) )
            {
                throw new TransformScriptException( "Tracked entity lookup script of FHIR resource mapping " +
                    resourceMapping.getFhirResourceType() + " did not return a tracked entity instance." );
            }
            return (result == null) ? null : ((TrackedEntityInstance) result);
        }
        catch ( ScriptCompilationException e )
        {
            throw new TransformScriptException( "Tracked entity lookup script of FHIR resource mapping " +
                resourceMapping.getFhirResourceType() + " caused an error: " + e.getMessage(), e );
        }
    }

    @Nullable
    protected String getEnrolledOrgUnitId( @Nonnull FhirResourceMapping resourceMapping, @Nonnull Enrollment enrollment, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        final Map<String, Object> arguments = new HashMap<>( scriptArguments );
        arguments.put( TransformerScriptConstants.ENROLLMENT_ATTR_NAME,
            new ImmutableScriptedEnrollment( new WritableScriptedEnrollment( enrollment, dhisValueConverter ) ) );

        try
        {
            final Object result = getScriptEvaluator().evaluate( new StaticScriptSource(
                resourceMapping.getEnrolledOrgUnitIdLookupScript() ), new HashMap<>( arguments ) );
            return (result == null) ? enrollment.getId() : result.toString();
        }
        catch ( ScriptCompilationException e )
        {
            throw new TransformScriptException( "Enrolled organization unit ID lookup script of FHIR resource mapping " +
                resourceMapping.getFhirResourceType() + " caused an error: " + e.getMessage(), e );
        }
    }

    @Nonnull
    protected ZonedDateTime getEventDate( @Nonnull FhirResourceMapping resourceMapping, @Nonnull Enrollment enrollment, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        final Map<String, Object> arguments = new HashMap<>( scriptArguments );
        arguments.put( TransformerScriptConstants.ENROLLMENT_ATTR_NAME,
            new ImmutableScriptedEnrollment( new WritableScriptedEnrollment( enrollment, dhisValueConverter ) ) );

        try
        {
            final Object result = getScriptEvaluator().evaluate( new StaticScriptSource(
                resourceMapping.getEventDateLookupScript() ), new HashMap<>( arguments ) );
            return (result == null) ? ZonedDateTime.now() : Objects.requireNonNull( dhisValueConverter.convert( result, ValueType.DATETIME, ZonedDateTime.class ) );
        }
        catch ( ScriptCompilationException e )
        {
            throw new TransformScriptException( "Event date lookup script of FHIR resource mapping " +
                resourceMapping.getFhirResourceType() + " caused an error: " + e.getMessage(), e );
        }
        catch ( ConversionException e )
        {
            throw new TransformScriptException( "Event date lookup script of FHIR resource mapping " +
                resourceMapping.getFhirResourceType() + " returned non-convertible date/time: " + e.getMessage(), e );
        }
    }

    @Nonnull
    private EventInfo getEventInfo( @Nonnull Predicate<Event> predicate, @Nonnull Map<String, Object> scriptArguments )
    {
        final Program program = (Program) scriptArguments.get( TransformerScriptConstants.PROGRAM_ATTR_NAME );
        if ( program == null )
        {
            throw new TransformFatalException( "Program is not included as script argument." );
        }
        final ProgramStage programStage = getProgramStage( scriptArguments );

        final TrackedEntityType trackedEntityType = (TrackedEntityType) scriptArguments.get( TransformerScriptConstants.TRACKED_ENTITY_TYPE_ATTR_NAME );
        if ( trackedEntityType == null )
        {
            throw new TransformFatalException( "Tracked entity type is not included as script argument." );
        }
        final ScriptedTrackedEntityInstance trackedEntityInstance = (ScriptedTrackedEntityInstance) scriptArguments.get( TransformerScriptConstants.TRACKED_ENTITY_INSTANCE_ATTR_NAME );
        if ( trackedEntityInstance == null )
        {
            throw new TransformFatalException( "Tracked entity instance is not included as script argument." );
        }

        // automatic enrollment is not available currently
        final Enrollment enrollment = enrollmentService.getLatestActive( program.getId(),
            Objects.requireNonNull( trackedEntityInstance.getId() ) ).orElse( null );
        Event event = null;
        boolean completedEvents = false;
        if ( enrollment != null )
        {
            final List<Event> events = eventService.find( program.getId(), programStage.getId(), enrollment.getId(), trackedEntityInstance.getId() );
            event = events.stream().filter( predicate ).sorted().findFirst().map( e -> {
                e.setEnrollment( enrollment );
                return e;
            } ).orElse( null );
            completedEvents = events.stream().anyMatch( e -> e.getStatus() == EventStatus.COMPLETED );
        }
        return new EventInfo( program, programStage, trackedEntityType, trackedEntityInstance, enrollment, event, completedEvents );
    }

    protected boolean transform( @Nonnull String script, @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        try
        {
            final Object result = getScriptEvaluator().evaluate( new StaticScriptSource( script ), new HashMap<>( scriptArguments ) );
            if ( !(result instanceof Boolean) )
            {
                throw new TransformScriptException( "Script did not return a boolean value." );
            }
            return (boolean) result;
        }
        catch ( ScriptCompilationException e )
        {
            throw new TransformScriptException( "Script caused an error: " + e.getMessage(), e );
        }
    }

    private static @Nonnull
    ProgramStage getProgramStage( @Nonnull Map<String, Object> scriptArguments ) throws TransformException
    {
        final ProgramStage programStage = (ProgramStage) scriptArguments.get( TransformerScriptConstants.PROGRAM_STAGE_ATTR_NAME );
        if ( programStage == null )
        {
            throw new TransformFatalException( "Program stage is not included as script argument." );
        }
        return programStage;
    }

    private static boolean isEditableEvent( @Nonnull Event e )
    {
        return (e.getStatus() == EventStatus.ACTIVE) || (e.getStatus() == EventStatus.SCHEDULE) || (e.getStatus() == EventStatus.OVERDUE) || (e.getStatus() == EventStatus.VISITED);
    }
}

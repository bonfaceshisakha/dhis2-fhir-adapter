package org.dhis2.fhir.adapter.prototype.fhir.transform.scripted;

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

import org.dhis2.fhir.adapter.prototype.dhis.model.DhisResource;
import org.dhis2.fhir.adapter.prototype.dhis.model.DhisResourceType;
import org.dhis2.fhir.adapter.prototype.fhir.metadata.model.AbstractFhirToDhisMapping;
import org.dhis2.fhir.adapter.prototype.fhir.model.FhirVersion;
import org.dhis2.fhir.adapter.prototype.fhir.transform.FhirToDhisTransformOutcome;
import org.dhis2.fhir.adapter.prototype.fhir.transform.FhirToDhisTransformerContext;
import org.dhis2.fhir.adapter.prototype.fhir.transform.TransformException;
import org.hl7.fhir.instance.model.api.IAnyResource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public interface FhirToDhisTransformer<R extends DhisResource, M extends AbstractFhirToDhisMapping>
{
    @Nullable
    FhirVersion getFhirVersion();

    @Nonnull
    DhisResourceType getDhisResourceType();

    @Nonnull
    Class<R> getDhisResourceClass();

    @Nonnull
    Class<M> getMappingClass();

    boolean addScriptArguments( @Nonnull Map<String, Object> arguments, @Nonnull FhirToDhisTransformerContext context, @Nonnull M mapping ) throws TransformException;

    boolean addScriptArgumentsCasted( @Nonnull Map<String, Object> arguments, @Nonnull FhirToDhisTransformerContext context, @Nonnull AbstractFhirToDhisMapping mapping ) throws TransformException;

    @Nullable
    FhirToDhisTransformOutcome<R> transform( @Nonnull FhirToDhisTransformerContext context, @Nonnull IAnyResource input, @Nonnull M mapping,
        @Nonnull Map<String, Object> scriptArguments ) throws TransformException;

    @Nullable
    FhirToDhisTransformOutcome<R> transformCasted( @Nonnull FhirToDhisTransformerContext context, @Nonnull IAnyResource input, @Nonnull AbstractFhirToDhisMapping mapping,
        @Nonnull Map<String, Object> scriptArguments ) throws TransformException;
}

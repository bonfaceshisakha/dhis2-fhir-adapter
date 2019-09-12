package org.dhis2.fhir.adapter.converter;


import org.dhis2.fhir.adapter.model.ValueType;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Converts an integer value to string. 
 * This converter should tackle 'No suitable converter for value type INTEGER and object type Integer.'
 *
 * @author Charles Chigoriwa (ITINordic)
 */
@Component
@ConvertedValueTypes( types = ValueType.INTEGER)
public class IntegerToStringConverter extends TypedConverter<Integer, String>
{
    public IntegerToStringConverter()
    {
        super( Integer.class, String.class );
    }

    @Nullable
    @Override
    public String doConvert( @Nonnull Integer source ) throws ConversionException
    {
        return source.toString();
    }
}

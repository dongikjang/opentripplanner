package org.opentripplanner.ext.transmodelapi.model.scalars;

import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLScalarType;
import org.opentripplanner.routing.api.request.framework.CostLinearFunction;

public class CostLinearFunctionFactory {

  private static final String DOCUMENTATION =
    "A linear function to calculate a value(y) based on a parameter (x): " +
    "`y = f(x) = a + bx`. It allows setting both a constant(a) and a coefficient(b) and " +
    "the use those in the computation. Format: `a + b x`. Example: `1800 + 2.0 x`";

  private CostLinearFunctionFactory() {}

  public static GraphQLScalarType createDoubleFunctionScalar() {
    return GraphQLScalarType
      .newScalar()
      .name("DoubleFunction")
      .description(DOCUMENTATION)
      .coercing(
        new Coercing<CostLinearFunction, String>() {
          @Override
          public String serialize(Object dataFetcherResult) {
            return ((CostLinearFunction) dataFetcherResult).serialize();
          }

          @Override
          public CostLinearFunction parseValue(Object input) throws CoercingParseValueException {
            try {
              return CostLinearFunction.of((String) input);
            } catch (IllegalArgumentException e) {
              throw new CoercingParseValueException(e.getMessage(), e);
            }
          }

          @Override
          public CostLinearFunction parseLiteral(Object input)
            throws CoercingParseLiteralException {
            if (input instanceof StringValue) {
              return parseValue(((StringValue) input).getValue());
            }
            return null;
          }
        }
      )
      .build();
  }
}

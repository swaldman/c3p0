package com.mchange.v2.c3p0.cfg;

public final class Validators
{
    static abstract class AbstractValidator<T> implements Validator<T>
    {
        String param;

        AbstractValidator( String param )
        { this.param = param; }

        abstract T _validate( T value ) throws InvalidConfigException;

        @Override
        public T validate( T value ) throws InvalidConfigException
        {
            try
                { return _validate(value); }
            catch (InvalidConfigException ice)
                { throw ice; }
            catch (Exception e)
                { throw new InvalidConfigException("While validating '" + param + "', encountered Exception: " + e, e); }
        }
    }

    public static Validator<String> MarkSessionBoundaries = new AbstractValidator<String>("markSessionBoundaries")
    {
        @Override
        String _validate( String value ) throws InvalidConfigException
        {
            String out = value.toLowerCase();
            if ("always".equals(out)||"never".equals(out)||"if-no-statement-cache".equals(out))
                return out;
            else
                throw new InvalidConfigException(param + " must be one of 'always', 'never', or 'if-no-statement-cache'. Found '" + value + "'.");
        }
    };

    public static Validator<String> ContextClassLoaderSource = new AbstractValidator<String>("contextClassLoaderSource")
    {
        @Override
        String _validate( String value ) throws InvalidConfigException
        {
             String out = value.toLowerCase();
             if ("caller".equals(out)||"library".equals(out)||"none".equals(out))
                 return out;
             else
                 throw new InvalidConfigException(param + " must be one of 'caller', 'library', or 'none'. Found '" + value + "'.");
        }
    };

    private Validators()
    {}
}

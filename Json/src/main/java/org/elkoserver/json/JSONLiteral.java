package org.elkoserver.json;

import java.util.Collection;

/**
 * A literal JSON string, representing either a message or an object,
 * undergoing incremental construction.
 *
 * Users of this class should call one of the constructors to begin creation
 * of the literal, incrementally add to it using the various
 * {@link #addParameter addParameter()} methods, then finally complete it by
 * calling the {@link #finish} method.  After the literal is completed, it may
 * be used as another literal's parameter value, or its string form can be
 * extracted by calling {@link #sendableString}.
 */
public class JSONLiteral {

    /** The literal under construction */
    private final StringBuilder myStringBuilder;

    /** Start of this literal's portion of buffer. */
    private final int myStartPos;

    /** End of this literal's portion of buffer. */
    private int myEndPos;

    /** State of construction */
    private int myState;

    /** Encode control indicating how this literal is being encoded */
    private final EncodeControl myControl;

    /* The state values */
    private final int INITIAL  = 0; /* Have not yet added first parameter */
    private final int COMPLETE = 2; /* All done */

    /**
     * Begin a new literal that will be filled in incrementally, with an
     * externally provided buffer.
     *
     * @param stringBuilder  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    JSONLiteral(StringBuilder stringBuilder,
                              EncodeControl control)
    {
        myStringBuilder = stringBuilder;
        myStartPos = stringBuilder.length();
        myEndPos = myStartPos;
        myStringBuilder.append('{');
        myState = INITIAL;
        myControl = control;
    }

    /**
     * Begin a new literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public JSONLiteral(EncodeControl control) {
        this(new StringBuilder(1000), control);
    }

    /**
     * Begin a new literal that will be filled in incrementally.
     */
    public JSONLiteral() {
        this(EncodeControl.forClient);
    }

    public void addParameter(String param, JSONLiteral jsonLiteral) {
        addParameter(param, (Object) jsonLiteral);
    }

    /**
     * Add an arbitrary parameter to an incomplete literal.  Note that any
     * parameter value whose string representation encodes to null will be
     * ignored (i.e., not added to the literal).
     *
     * @param param  The parameter name.
     * @param value  The parameter value.
     *
     * @throws Error if you try to add a parameter to literal that is already
     *    complete.
     */
    public void addParameter(String param, Object value) {
        int start = myStringBuilder.length();
        beginParameter(param);
        if (appendValueString(myStringBuilder, value, myControl)) {
            myStringBuilder.setLength(start);
        }
    }

    /**
     * Add an optional arbitrary parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,Object)}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional parameter value.
     */
    public void addParameterOpt(String param, Object value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add an array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (array) parameter value.
     */
    public void addParameter(String param, Object[] value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (Object element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }

    /**
     * Add a collection parameter to an incomplete literal (as an array).
     *
     * @param param  The parameter name.
     * @param value  The (collection) parameter value.
     */
    public void addParameter(String param, Collection<?> value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (Object element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }

    /**
     * Add an optional array parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,Object[])}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (array) parameter value.
     */
    public void addParameterOpt(String param, Object[] value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add an optional collection parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,Collection)}, except that if the
     * value is null or the collection is empty, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (collection) parameter value.
     */
    public void addParameterOpt(String param, Collection<?> value) {
        if (value != null && value.size() > 0) {
            addParameter(param, value);
        }
    }

    /**
     * Add an int array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (int array) value.
     */
    private void addParameter(String param, int[] value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (int element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }

    /**
     * Add an optional int array parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,int[])}, except that if the value
     * is null, the parameter is not added.
     *
     * @param param  The parameter name
     * @param value  The optional (int array) parameter value
     */
    public void addParameterOpt(String param, int[] value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a long array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (long array) value.
     */
    private void addParameter(String param, long[] value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (long element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }
    
    /**
     * Add an optional long array parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,long[])}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (long array) parameter value.
     */
    public void addParameterOpt(String param, long[] value) {
        if (value != null) {
            addParameter(param, value);
        }
    }
    
    /**
     * Add a double array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (double array) value.
     */
    private void addParameter(String param, double[] value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (double element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }

    /**
     * Add an optional double array parameter to an incomplete literal.  This
     * is similar to {@link #addParameter(String,double[])}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (double array) parameter value.
     */
    public void addParameterOpt(String param, double[] value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a boolean array parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (boolean array) value.
     */
    private void addParameter(String param, boolean[] value) {
        beginParameter(param);
        JSONLiteralArray arr =
            new JSONLiteralArray(myStringBuilder, myControl);
        for (boolean element : value) {
            arr.addElement(element);
        }
        arr.finish();
    }

    /**
     * Add an optional boolean array parameter to an incomplete literal.  This
     * is similar to {@link #addParameter(String,boolean[])}, except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (boolean array) parameter value.
     */
    public void addParameterOpt(String param, boolean[] value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a {@link JsonArray} parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The parameter (JSONArray) value.
     */
    private void addParameter(String param, JsonArray value) {
        addParameter(param, (Object) value);
    }

    /**
     * Add an optional {@link JsonArray} parameter to an incomplete
     * literal.  This is similar to {@link #addParameter(String,JsonArray)},
     * except that if the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional (JSONArray) parameter value.
     */
    public void addParameterOpt(String param, JsonArray value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add an array literal parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ({@link JSONLiteralArray}) parameter value.
     */
    public void addParameter(String param, JSONLiteralArray value) {
        addParameter(param, (Object) value);
    }

    /**
     * Add an optional array literal parameter to an incomplete literal.  This
     * is similar to {@link #addParameter(String,JSONLiteralArray)}, except
     * that if the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional ({@link JSONLiteralArray}) parameter value
     */
    public void addParameterOpt(String param, JSONLiteralArray value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add an object-valued parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ({@link Encodable}) parameter value.
     */
    public void addParameter(String param, Encodable value) {
        addParameter(param, (Object) value);
    }

    /**
     * Add an optional object-valued parameter to an incomplete literal.  This
     * is similar to {@link #addParameter(String,Encodable)}, except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The optional ({@link Encodable}) parameter value.
     */
    public void addParameterOpt(String param, Encodable value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a floating point parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (double) parameter value.
     */
    public void addParameter(String param, double value) {
        addParameter(param, Double.valueOf(value));
    }

    /**
     * Add a boolean parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (boolean) parameter value.
     */
    public void addParameter(String param, boolean value) {
        addParameter(param, Boolean.valueOf(value));
    }

    /**
     * Add an integer parameter to an incomplete literal.
     *
     * @param param  The parameter name
     * @param value  The (int) parameter value.
     */
    public void addParameter(String param, int value) {
        addParameter(param, Integer.valueOf(value));
    }

    /**
     * Add a long parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The (long) parameter value.
     */
    public void addParameter(String param, long value) {
        addParameter(param, Long.valueOf(value));
    }

    /**
     * Add a JSON object parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ({@link JsonObject}) parameter value.
     */
    public void addParameter(String param, JsonObject value) {
        addParameter(param, (Object) value);
    }

    /**
     * Add an optional JSON object parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,JsonObject)}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The ({@link JsonObject}) parameter value.
     */
    public void addParameterOpt(String param, JsonObject value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a reference parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ({@link Referenceable}) parameter value.
     */
    public void addParameter(String param, Referenceable value) {
        addParameter(param, (Object) value.ref());
    }

    /**
     * Add an optional reference parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,Referenceable)}, except that if
     * the value is null, the parameter is not added.
     *
     * @param param  The parameter name
     * @param value  The parameter value
     */
    public void addParameterOpt(String param, Referenceable value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Add a string parameter to an incomplete literal.
     *
     * @param param  The parameter name.
     * @param value  The ({@link String}) parameter value.
     */
    public void addParameter(String param, String value) {
        addParameter(param, (Object) value);
    }

    /**
     * Add an optional string parameter to an incomplete literal.  This is
     * similar to {@link #addParameter(String,String)}, except that if the
     * value is null, the parameter is not added.
     *
     * @param param  The parameter name.
     * @param value  The parameter value.
     */
    public void addParameterOpt(String param, String value) {
        if (value != null) {
            addParameter(param, value);
        }
    }

    /**
     * Common base behavior for beginning encoding of any parameter.  Prepend
     * any necessary punctuation upon starting a new element, add the parameter
     * name, and update the state of construction accordingly.
     *
     * @param param  The parameter name.
     */
    private void beginParameter(String param) {
        if (myState != COMPLETE) {
            if (myState == INITIAL) {
                /* Have added first parameter */
                // 1 = STARTED
                myState = 1;
            } else {
                myStringBuilder.append(", ");
            }
            myStringBuilder.append('"');
            myStringBuilder.append(param);
            myStringBuilder.append("\":");
        } else {
            throw new Error("attempt to add parameter to completed literal");
        }
    }

    /**
     * Obtain the encode control governing this literal.
     *
     * @return this literal's encode control.
     */
    public EncodeControl control() {
        return myControl;
    }

    /**
     * Finish construction of the literal.
     *
     * @throws Error if you try to finish a literal that is already complete.
     */
    public void finish() {
        if (myState != COMPLETE) {
            myStringBuilder.append('}');
            myState = COMPLETE;
            myEndPos = myStringBuilder.length();
        } else {
            throw new Error("attempt to finish already completed literal");
        }
    }

    /**
     * Obtain the number of characters in the literal.
     *
     * @return the number of characters currently in this literal.
     */
    public int length() {
        return myEndPos - myStartPos;
    }

    /**
     * Obtain a string representation of this literal suitable for output to a
     * connection.
     *
     * @return a sendable string representation of this literal.
     *
     * @throws Error if the literal is not finished.
     */
    public String sendableString() {
        if (myState != COMPLETE) {
            finish();
        }
        return myStringBuilder.substring(myStartPos, myEndPos);
    }

    /**
     * Obtain a printable string representation of this literal, in whatever
     * its current state is.
     *
     * @return a printable representation of this literal.
     */
    public String toString() {
        int end = myEndPos;
        if (myState != COMPLETE) {
            end = myStringBuilder.length();
        }
        return myStringBuilder.substring(myStartPos, end);
    }

    /**
     * Convert a value of any type into the appropriate characters appended
     * onto the string under construction.
     *
     * @param buf  Buffer into which to encode the given value.
     * @param value  The value whose string encoding is sought
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     *
     * @return true if the given value could not be encoded and so should be
     *    ignored, false if everything worked fine.
     */
    static boolean appendValueString(StringBuilder buf, Object value,
                                     EncodeControl control) {
        if (value == null) {
            /* Null is a special value all its own */
            buf.append("null");
        } else if (value instanceof String) {
            String str = (String) value;
            buf.append('"');
            int start = 0;
            for (int i = 0; i < str.length(); ++i) {
                char c = str.charAt(i);
                char escape = '*';
                switch (c) {
                    case '"':  escape = '"';  break;
                    case '\\': escape = '\\'; break;
                    case '\b': escape = 'b';  break;
                    case '\f': escape = 'f';  break;
                    case '\n': escape = 'n';  break;
                    case '\r': escape = 'r';  break;
                    case '\t': escape = 't';  break;
                }
                if (escape != '*') {
                    buf.append(str, start, i);
                    start = i + 1;
                    buf.append('\\');
                    buf.append(escape);
                }
            }
            buf.append(str.substring(start));
            buf.append('"');
        } else if (value instanceof Number) {
            buf.append(value.toString());
        } else if (value instanceof Encodable) {
            /* If the value knows how, ask it to encode itself */
            JSONLiteral encoded = ((Encodable) value).encode(control);
            if (encoded != null) {
                String result = encoded.sendableString();
                buf.append(result);
            } else {
                return true;
            }
        } else if (value instanceof JSONLiteral) {
            buf.append(((JSONLiteral) value).myStringBuilder);
        } else if (value instanceof JSONLiteralArray) {
            buf.append(((JSONLiteralArray) value).stringBuilder());
        } else if (value instanceof JsonObject) {
            JsonObjectSerialization.encodeLiteral((JsonObject) value, buf, control);
        } else if (value instanceof JsonArray) {
            JsonArraySerialization.encodeLiteral((JsonArray) value, buf, control);
        } else {
            /* Else just convert the value to its natural string form */
            buf.append(value.toString());
        }
        return false;
    }
}

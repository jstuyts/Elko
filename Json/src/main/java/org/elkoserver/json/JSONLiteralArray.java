package org.elkoserver.json;

/**
 * A literal JSON string, representing an array, undergoing incremental
 * construction.
 *
 * Users of this class should call the constructor to begin creation of the
 * literal, incrementally add to it using the various
 * {@link #addElement addElement()} methods, then finally complete it by
 * calling the {@link #finish} method.  After the literal is completed, it may
 * be used as another literal's parameter value.
 */
public class JSONLiteralArray {
    /** The string under construction. */
    private StringBuilder myStringBuilder;

    /** Start of this literal's portion of buffer. */
    private int myStartPos;

    /** End of this literal's portion of buffer. */
    private int myEndPos;

    /** State of construction. */
    private int myState;

    /** Number of elements successfully added. */
    private int mySize;

    /** Encode control to indicate how this literal is being encoded */
    private EncodeControl myControl;

    /* The state values */
    private final int INITIAL  = 0; /* Have not yet added first element */
    private final int STARTED  = 1; /* Have added first element */
    private final int COMPLETE = 2; /* All done */

    /**
     * Begin a new array literal that will be filled in incrementally, with an
     * externally provided buffer.
     *
     * @param stringBuilder  The buffer into which to build the literal string.
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    JSONLiteralArray(StringBuilder stringBuilder,
                            EncodeControl control)
    {
        myStringBuilder = stringBuilder;
        myStartPos = stringBuilder.length();
        myEndPos = myStartPos;
        myStringBuilder.append("[");
        myState = INITIAL;
        mySize = 0;
        myControl = control;
    }

    /**
     * Begin a new array literal that will be filled in incrementally.
     *
     * @param control  Encode control determining what flavor of encoding
     *    is being done.
     */
    public JSONLiteralArray(EncodeControl control) {
        this(new StringBuilder(500), control);
    }

    /**
     * Begin a new array literal that will be filled in incrementally.
     */
    public JSONLiteralArray() {
        this(EncodeControl.forClient);
    }

    /**
     * Add an element to the incomplete array literal. Note that any element
     * value that encodes to the Java value null will be ignored (i.e., not
     * added to the literal).
     *
     * @param value  The element value.
     *
     * @throws Error if you try to add an element to literal that is already
     *    complete.
     */
    public void addElement(Object value) {
        if (value instanceof Object[]) {
            beginElement();
            Object[] valueArray = (Object[]) value;
            JSONLiteralArray arr =
                new JSONLiteralArray(myStringBuilder, myControl);
            for (Object o : valueArray) {
                arr.addElement(o);
            }
            arr.finish();
        } else if (value != null) {
            int start = myStringBuilder.length();
            boolean starting = (myState == INITIAL);
            beginElement();
            if (JSONLiteral.appendValueString(myStringBuilder, value,
                                              myControl)) {
                myStringBuilder.setLength(start);
                if (starting) {
                    myState = INITIAL;
                }
            } else {
                mySize += 1;
            }
        }
    }

    public void addElement(JSONLiteral jsonLiteral) {
        addElement((Object)jsonLiteral);
    }

    public void addElement(JSONLiteralArray jsonLiteralArray) {
        addElement((Object)jsonLiteralArray);
    }

    public void addElement(Boolean bool) {
        addElement((Object)bool);
    }

    public void addElement(Number number) {
        addElement((Object)number);
    }

    public void addElement(String string) {
        addElement((Object)string);
    }

    /**
     * Add a reference element to an incomplete array.
     *
     * @param value  The ({@link Referenceable}) element value.
     */
    public void addElement(Referenceable value) {
        addElement(value.ref());
    }

    /**
     * Prepend any necessary punctuation upon starting a new element, and
     * update the state of construction accordingly.
     */
    private void beginElement() {
        if (myState != COMPLETE) {
            if (myState == INITIAL) {
                /* Have added first element */
                myState = STARTED;
            } else {
                myStringBuilder.append(", ");
            }
        } else {
            throw new Error("attempt to add element to completed array");
        }
    }

    /**
     * Obtain the encode control governing this literal.
     *
     * @return this literal array's encode control.
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
            myStringBuilder.append(']');
            myState = COMPLETE;
            myEndPos = myStringBuilder.length();
        } else {
            throw new Error("attempt to finish already completed array");
        }
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
     * Obtain the array's element count.
     *
     * @return the number of elements in this array (so far).
     */
    public int size() {
        return mySize;
    }

    /**
     * Get the internal string buffer, for collusion with JSONLiteral.
     */
    StringBuilder stringBuilder() {
        return myStringBuilder;
    }

    /**
     * Obtain a printable String representation of this literal, in whatever
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
}

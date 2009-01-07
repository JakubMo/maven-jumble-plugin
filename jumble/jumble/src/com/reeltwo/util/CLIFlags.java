package com.reeltwo.util;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This class is a handy utility for dealing with command line flags. It allows
 * syntactic and type-checking of flags. Here is some example usage:
 * <p>
 * 
 * <code><pre>
 * public static void main(String[] args) {
 *   // Create the CLIFlags
 *   CLIFlags flags = new CLIFlags(&quot;java Example&quot;);
 * 
 *   // Simple register, a boolean option, no description
 *   flags.registerOptional(&quot;verbose&quot;);
 *   // Register a required integer flag with usage and long description.
 *   flags.registerRequired(&quot;port&quot;, Integer.class, &quot;NUMBER&quot;, &quot;The port to connect to&quot;); // does type-checking!
 *   flags.setRemainderUsage(&quot; &lt; FILE&quot;);
 * 
 *   // Set the user-supplied flags with main's String[] args
 *   // This will attempt to parse the flags.
 *   // If it cannot it will print out an appropriate message and call System.exit.
 *   // To override this behaviour, see setInvalidFlagHandler()
 *   flags.setFlags(args);
 * 
 *   // Read the flags
 *   if (flags.isSet(&quot;port&quot;)) {
 *     Integer port = (Integer) flags.getValue(&quot;port&quot;);
 *   }
 * 
 *   // Rest of your code follows here.
 * 
 * }
 * </pre></code>
 * 
 * @author <a href="mailto:pablo@reeltwo.com">Pablo Mayrgundter</a>
 * @author <a href="mailto:len@reeltwo.com">Len Trigg</a>
 * @version $Revision: 1.111 $
 * @since 1.0
 */
public final class CLIFlags {

  private static final String LS = System.getProperty("line.separator");

  /**
   * An interface for objects that perform custom flag validation across a set
   * of flags.
   */
  public interface Validator {

    /**
     * Returns false if the current flag settings are not valid. An error
     * message should be supplied by calling flags.setParseMessage()
     * 
     * @param flags a <code>CLIFlags</code>.
     * @return true if the current flag settings are valid.
     */
    boolean isValid(CLIFlags flags);
  }

  /**
   * An interface for objects that handle invalid flag settings.
   */
  public interface InvalidFlagHandler {

    /**
     * Perform whatever action you want to take if flags are determined to be
     * invalid.
     * 
     * @param flags a <code>CLIFlags</code>.
     */
    void handleInvalidFlags(CLIFlags flags);
  }

  private static class FlagCountException extends IllegalArgumentException {
    public FlagCountException(final String message) {
      super(message);
    }
  }

  /**
   * Encapsulates a single flag.
   */
  public static class Flag implements Comparable {

    private final Character mFlagChar;

    private final String mFlagName;

    private final String mFlagDescription;

    /** The maximum number of times the flag can occur. */
    private int mMaxCount;

    /** The minimum number of times the flag can occur. */
    private int mMinCount;

    private final Class mParameterType;

    private final String mParameterDescription;

    private final Object mParameterDefault;

    /** Optional list of valid values for the parameter. */
    private List<String> mParameterRange;

    /** Values supplied by the user */
    private List<Object> mParameter = new ArrayList<Object>();

    /**
     * Creates a new <code>Flag</code> for which the name must be supplied on
     * the command line.
     * 
     * @param flagChar a <code>Character</code> which can be supplied by the
     * user as an abbreviation for flagName. May be null.
     * @param flagName a <code>String</code> which is the name that the user
     * specifies on the command line to denote the flag.
     * @param flagDescription a name used when printing help messages.
     * @param minCount the minimum number of times the flag must be specified.
     * @param maxCount the maximum number of times the flag can be specified.
     * @param paramType a <code>Class</code> denoting the type of values to be
     * accepted. Maybe null for "switch" type flags.
     * @param paramDescription a description of the meaning of the flag.
     * @param paramDefault a default value that can be used for optional flags.
     */
    public Flag(Character flagChar, final String flagName, final String flagDescription,
        final int minCount, final int maxCount, Class paramType, final String paramDescription,
        Object paramDefault) {
      if (flagDescription == null) {
        throw new NullPointerException();
      }
      if ((flagName == null) && (paramType == null)) {
        throw new IllegalArgumentException();
      }

      mFlagName = flagName;
      mFlagChar = flagChar;
      mFlagDescription = flagDescription;

      mParameterType = paramType;
      mParameterDescription = (mParameterType == null) ? null
          : ((paramDescription == null) || (paramDescription.length() == 0)) ? autoDescription(mParameterType)
              : paramDescription.toUpperCase();
      mParameterDefault = (mParameterType == null) ? null : paramDefault;

      mMinCount = minCount;
      mMaxCount = maxCount;

      // For enums set up the limited set of values message
      if (mParameterType != null && mParameterType.isEnum()) {
        try {
          Method m = mParameterType.getMethod("values", new Class[0]);
          Enum[] ret = (Enum[]) m.invoke(null, new Object[0]);
          List<String> s = new ArrayList<String>();
          
          for (Enum e : ret) {
            s.add(e.toString());
          }
          setParameterRange(s);
        } catch (NoSuchMethodException e) {
          // Should never happen
          throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
          // Should never happen
          throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
          // Should never happen
          throw new RuntimeException(e);
        }
      }
    }

    /**
     * Sets the maximum number of times the flag can be specified.
     * 
     * @param count the maximum number of times the flag can be specified.
     */
    public void setMaxCount(final int count) {
      if ((count < 1) || (count < mMinCount)) {
        throw new IllegalArgumentException("MaxCount (" + count
            + ") must not be 0 or less than MinCount (" + mMinCount + ")");
      }
      mMaxCount = count;
    }

    /**
     * Gets the maximum number of times the flag can be specified.
     * 
     * @return the maximum number of times the flag can be specified.
     */
    public int getMaxCount() {
      return mMaxCount;
    }

    /**
     * Sets the minimum number of times the flag can be specified.
     * 
     * @param count the minimum number of times the flag can be specified.
     */
    public void setMinCount(final int count) {
      if (count > mMaxCount) {
        throw new IllegalArgumentException("MinCount (" + count
            + ") must not be greater than MaxCount (" + mMaxCount + ")");
      }
      if (count == Integer.MAX_VALUE) {
        throw new IllegalArgumentException(
            "You're crazy man -- MinCount cannot be Integer.MAX_VALUE");
      }
      mMinCount = count;
    }

    /**
     * Gets the minimum number of times the flag can be specified.
     * 
     * @return the minimum number of times the flag can be specified.
     */
    public int getMinCount() {
      return mMinCount;
    }

    /**
     * Return the number of times the flag has been set.
     * 
     * @return the number of times the flag has been set.
     */
    public int getCount() {
      return mParameter.size();
    }

    /**
     * Return true if the flag has been set.
     * 
     * @return true if the flag has been set.
     */
    public boolean isSet() {
      return mParameter.size() > 0;
    }

    /**
     * Gets the character name of this flag, if set.
     * 
     * @return the character name of this flag, or null if no character name has
     * been set.
     */
    public Character getChar() {
      return mFlagChar;
    }

    /**
     * Gets the name of the flag.
     * 
     * @return the name of the flag.
     */
    public String getName() {
      return mFlagName;
    }

    /**
     * Gets the description of the flag's purpose.
     * 
     * @return the description.
     */
    public String getDescription() {
      return mFlagDescription;
    }

    /**
     * Gets the description of the flag parameter. This is usually a single word
     * that indicates a little more than the parameter type.
     * 
     * @return the parameter description, or null for untyped flags.
     */
    public String getParameterDescription() {
      return mParameterDescription;
    }

    /**
     * Gets the type of the parameter. This will return null for untyped
     * (switch) flags. Parameters will be checked that they are of the specified
     * type.
     * 
     * @return the parameter type, or null if the flag is untyped.
     */
    public Class getParameterType() {
      return mParameterType;
    }

    /**
     * Gets the default value of the parameter.
     * 
     * @return the default value, or null if no default has been specified.
     */
    public Object getParameterDefault() {
      return mParameterDefault;
    }

    /**
     * Defines the set of strings that are valid for this flag.
     * 
     * @param range a collection of Strings.
     */
    public void setParameterRange(Collection<String> range) {
      setParameterRange(range.toArray(new String[range.size()]));
    }

    /**
     * Defines the set of strings that are valid for this flag.
     * 
     * @param range an array of Strings.
     */
    public void setParameterRange(String[] range) {
      if (mParameterType == null) {
        throw new IllegalArgumentException("Cannot set parameter range for no-arg flags.");
      }
      if (range == null) {
        mParameterRange = null;
      } else {
        if (range.length < 2) {
          throw new IllegalArgumentException("Must specify at least two values in parameter range.");
        }
        List<String> l = new ArrayList<String>();
        for (int i = 0; i < range.length; i++) {
          final String s = range[i];
          try {
            parseValue(s);
          } catch (Exception e) {
            throw new IllegalArgumentException("Range value " + s + " could not be parsed.");
          }
          l.add(s);
        }
        mParameterRange = Collections.unmodifiableList(l);
      }
    }

    /**
     * Gets the list of valid parameter values, if these have been specified.
     * 
     * @return a <code>List</code> containing the permitted values, or null if
     * this has not been set.
     */
    public List<String> getParameterRange() {
      return mParameterRange;
    }

    /**
     * Get the value for this flag. If the flag was not user-set, then the
     * default value is returned (if defined). The value will have been checked
     * to comply with any parameter typing. If called on an untyped flag, this
     * will return Boolean.TRUE or Boolean.FALSE appropriately.
     * 
     * @return a value for this flag.
     */
    public Object getValue() {
      return (isSet()) ? mParameter.get(0) : (mParameterType == null) ? Boolean.FALSE
          : mParameterDefault;
    }

    /**
     * Get a collection of all values set for this flag. This is for flags that
     * can be set multiple times. If the flag was not user-set, then the
     * collection contains only the default value (if defined).
     * 
     * @return a <code>Collection</code> of the supplied values.
     */
    public Collection<Object> getValues() {
      final Collection<Object> result;
      if (isSet()) {
        result = mParameter;
      } else {
        result = new ArrayList<Object>();
        if (mParameterType == null) {
          result.add(Boolean.FALSE);
        } else if (mParameterDefault != null) {
          result.add(mParameterDefault);
        }
      }
      return Collections.unmodifiableCollection(result);
    }

    private void reset() {
      mParameter = new ArrayList<Object>();
    }

    private FlagValue setValue(final String valueStr) {
      if (mParameter.size() >= mMaxCount) {
        throw new FlagCountException("Value cannot be set more than once for flag: " + mFlagName);
      }
      if ((mParameterRange != null) && !mParameterRange.contains(valueStr)) {
        throw new IllegalArgumentException("Value supplied is not in the set of allowed values.");
      }
      Object value = parseValue(valueStr);
      mParameter.add(value);
      return new FlagValue(this, value);
    }

    /**
     * Converts the string representation of a parameter value into the
     * appropriate Object. This default implementation knows how to convert
     * based on the parameter type for several common types. Override for custom
     * parsing.
     */
    protected Object parseValue(final String valueStr) {
      return (mParameterType == null) ? Boolean.TRUE : Flag
          .instanceHelper(mParameterType, valueStr);
    }

    /** {@inheritDoc} */
    public boolean equals(final Object other) {
      return other instanceof Flag && getName().equals(((Flag) other).getName());
    }

    /** {@inheritDoc} */
    public int hashCode() {
      return getName().hashCode();
    }

    /** {@inheritDoc} */
    public int compareTo(Object other) {
      if ((other == null) || !(other instanceof Flag)) {
        return -1;
      }
      Flag f2 = (Flag) other;
      if (f2.getName() != null) {
        return (getName().compareTo(f2.getName()));
      }
      return -1;
    }

    /** Make a compact usage string (prefers char name if present). */
    protected String getCompactFlagUsage() {
      StringBuffer sb = new StringBuffer();
      if (getChar() != null) {
        sb.append(SHORT_FLAG_PREFIX).append(getChar());
      } else {
        sb.append(LONG_FLAG_PREFIX).append(getName());
      }
      final String usage = getParameterDescription();
      if (usage.length() > 0) {
        sb.append(' ').append(usage);
      }
      return sb.toString();
    }

    /** Make a usage string. */
    protected String getFlagUsage() {
      StringBuffer sb = new StringBuffer();
      sb.append(LONG_FLAG_PREFIX).append(getName());
      if (getParameterType() != null) {
        sb.append('=').append(getParameterDescription());
      }
      return sb.toString();
    }

    private void appendLongFlagUsage(WrappingStringBuffer wb, final int longestUsageLength) {
      wb.append("  ");
      if (getChar() == null) {
        wb.append("    ");
      } else {
        wb.append(SHORT_FLAG_PREFIX).append(getChar().charValue()).append(", ");
      }

      final String usageStr = getFlagUsage();
      wb.append(getFlagUsage());
      for (int i = 0; i < longestUsageLength - usageStr.length(); i++) {
        wb.append(" ");
      }
      wb.append(" ");

      StringBuffer description = new StringBuffer(getDescription());

      List range = getParameterRange();
      if (range != null) {
        description.append(" Must be one of ").append(range).append(".");
      }

      if (getMinCount() > 1) {
        description.append(" Must be specified at least ").append(getMinCount()).append(" times.");
      }

      if (getMaxCount() > 1) {
        if (getMaxCount() == Integer.MAX_VALUE) {
          description.append(" May be specified multiple times.");
        } else {
          description.append(" May be specified up to ").append(getMaxCount()).append(" times.");
        }
      }

      if (getParameterDefault() != null) {
        description.append(" (Default is ").append(getParameterDefault()).append(")");
      }

      wb.wrapText(description.toString());
      wb.append(LS);
    }

    private static String autoDescription(Class type) {
      final String result = type.getName();
      return result.substring(result.lastIndexOf('.') + 1).toUpperCase();
    }

    private static final Set<String> BOOLEAN_AFFIRMATIVE = new HashSet<String>();
    {
      BOOLEAN_AFFIRMATIVE.add("yes");
      BOOLEAN_AFFIRMATIVE.add("y");
      BOOLEAN_AFFIRMATIVE.add("t");
      BOOLEAN_AFFIRMATIVE.add("1");
      BOOLEAN_AFFIRMATIVE.add("on");
      BOOLEAN_AFFIRMATIVE.add("aye");
      BOOLEAN_AFFIRMATIVE.add("hai");
      BOOLEAN_AFFIRMATIVE.add("ja");
      BOOLEAN_AFFIRMATIVE.add("da");
      BOOLEAN_AFFIRMATIVE.add("ya");
      BOOLEAN_AFFIRMATIVE.add("positive");
      BOOLEAN_AFFIRMATIVE.add("fer-shure");
      BOOLEAN_AFFIRMATIVE.add("totally");
      BOOLEAN_AFFIRMATIVE.add("affirmative");
      BOOLEAN_AFFIRMATIVE.add("+5v");
    }

    private static Object instanceHelper(Class type, final String stringRep) {
      try {
        if (type == Boolean.class) {
          Boolean result = Boolean.valueOf(stringRep);
          if (!result.booleanValue() && BOOLEAN_AFFIRMATIVE.contains(stringRep.toLowerCase())) {
            result = Boolean.TRUE;
          }
          return result;
        } else if (type == Byte.class) {
          return new Byte(stringRep);
        } else if (type == Character.class) {
          return new Character(stringRep.charAt(0));
        } else if (type == Float.class) {
          return new Float(stringRep);
        } else if (type == Double.class) {
          return new Double(stringRep);
        } else if (type == Integer.class) {
          return new Integer(stringRep);
        } else if (type == Long.class) {
          return new Long(stringRep);
        } else if (type == Short.class) {
          return new Short(stringRep);
        } else if (type == File.class) {
          return new File(stringRep);
        } else if (type == URL.class) {
          return new URL(stringRep);
        } else if (type == String.class) {
          return stringRep;
        } else if (type.isEnum()) {
          return Enum.valueOf(type, stringRep);
        } else if (type == Class.class) {
          return Class.forName(stringRep);
        }
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("");
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Badly formatted URL: " + stringRep);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Class not found: " + stringRep);
      }
      throw new IllegalArgumentException("Unknown parameter type: " + type);
    }
  }

  /**
   * <code>AnonymousFlag</code> is a flag with no name.
   */
  public static class AnonymousFlag extends Flag {

    private static int sAnonCounter = 0;

    /** This specifies the ordering. */
    private final int mFlagRank;

    /**
     * Constructor for an anonymous <code>Flag</code>. These flags aren't
     * referred to by name on the command line -- their values are assigned
     * based on their position in the command line.
     * 
     * @param flagDescription a name used when printing help messages.
     * @param paramType a <code>Class</code> denoting the type of values to be
     * accepted.
     * @param paramDescription a description of the meaning of the flag.
     */
    public AnonymousFlag(final String flagDescription, Class paramType,
        final String paramDescription) {
      super(null, null, flagDescription, 1, 1, paramType, paramDescription, null);
      mFlagRank = ++sAnonCounter;
    }

    /** {@inheritDoc} */
    protected String getFlagUsage() {
      StringBuffer sb = new StringBuffer();
      sb.append(getParameterDescription());
      if (getMaxCount() > 1) {
        sb.append('+');
      }
      return sb.toString();
    }

    /** {@inheritDoc} */
    protected String getCompactFlagUsage() {
      return getFlagUsage();
    }

    /** {@inheritDoc} */
    public boolean equals(final Object other) {
      return other instanceof Flag && getName().equals(((Flag) other).getName());
    }

    /** {@inheritDoc} */
    public int hashCode() {
      return getName().hashCode();
    }

    /** {@inheritDoc} */
    public int compareTo(Object other) {
      if (other instanceof AnonymousFlag) {
        return mFlagRank - ((AnonymousFlag) other).mFlagRank;
      }
      return 1;
    }
  }

  /**
   * Encapsulates a flag and value pairing. This is used when retrieving the set
   * of flags in the order they were set.
   */
  public static class FlagValue {
    private Flag mFlag;

    private Object mValue;

    private FlagValue(Flag flag, Object value) {
      mFlag = flag;
      mValue = value;
    }

    /**
     * Gets the Flag that this value was supplied to.
     * 
     * @return the Flag that this value was supplied to.
     */
    public Flag getFlag() {
      return mFlag;
    }

    /**
     * Gets the value supplied to the flag.
     * 
     * @return the value supplied to the flag.
     */
    public Object getValue() {
      return mValue;
    }

    /**
     * Gets a human-readable description of the flag value.
     * 
     * @return a human-readable description of the flag value.
     */
    public String toString() {
      String name = mFlag.getName();
      if (name == null) {
        name = mFlag.getParameterDescription();
      }
      return name + "=" + mValue;
    }
  }

  /**
   * True if the default InvalidFlagHandler is permitted to call System.exit.
   * This will be set to false if this class is loaded as part of JUnit tests.
   * (To prevent any tests of main from exiting the tests).
   */
  public static final boolean EXIT_OK;
  static {
    Throwable t = new Throwable();
    StackTraceElement[] trace = t.getStackTrace();
    boolean exitOk = true;
    for (int i = 0; i < trace.length && exitOk; i++) {
      if (trace[i].getClassName().startsWith("junit")) {
        exitOk = false;
      }
    }
    EXIT_OK = exitOk;
  }

  /** The default invalid flag handler. */
  public static final InvalidFlagHandler DEFAULT_INVALID_FLAG_HANDLER = new InvalidFlagHandler() {
    public void handleInvalidFlags(CLIFlags flags) {
      final int errorCode;
      if (flags.isSet(HELP_FLAG)) {
        flags.printUsage();
        errorCode = 0;
      } else {
        final WrappingStringBuffer wb = new WrappingStringBuffer();
        wb.setWrapWidth(DEFAULT_WIDTH);
        flags.appendUsageHeader(wb);
        flags.appendParseMessage(wb);
        wb.append(LS + "Try '" + LONG_FLAG_PREFIX + HELP_FLAG + "' for more information");
        System.err.println(wb.toString());
        errorCode = 1;
      }
      if (!EXIT_OK) {
        throw new IllegalArgumentException("Exit with: " + errorCode);
      }
      System.exit(errorCode);
    }
  };

  private static final String SHORT_FLAG_PREFIX = "-";

  private static final String LONG_FLAG_PREFIX = "--";

  /** The built-in flag that signals wants help about flag usage. */
  public static final String HELP_FLAG = "help";

  private static final String USAGE_SUMMARY_PREFIX = "Usage: ";

  private static final String PARSE_ERROR_PREFIX = "Error: ";

  private static final String REQUIRED_FLAG_USAGE_PREFIX = "Required flags: ";

  private static final String OPTIONAL_FLAG_USAGE_PREFIX = "Optional flags: ";

  /**
   * Default width to which help is printed. This is semi-intelligent in that it
   * attempts to look at environment variables to determine the terminal width.
   */
  private static final int DEFAULT_WIDTH;
  static {
    int defaultWidth = 80;
    try { // Have a crack at working out a larger default value
      Process p = Runtime.getRuntime().exec("printenv TERMCAP");
      p.waitFor();
      InputStream is = p.getInputStream();
      byte[] b = new byte[is.available()];
      if (is.read(b) == b.length) {
        final String columnsEnv = new String(b).toUpperCase();
        Matcher m = Pattern.compile(":CO#([0-9]+):").matcher(columnsEnv);
        if (m.find()) {
          defaultWidth = Integer.parseInt(m.group(1));
        }
      }
    } catch (Throwable t) {
      ; // We really don't care, just fall back on the default
    }
    // System.err.println("Default width is " + defaultWidth);
    DEFAULT_WIDTH = defaultWidth;
  }

  /** The set of all (named) registered flags */
  private Set<Flag> mRegisteredFlags;

  /** The list of anonymous flags */
  private List<Flag> mAnonymousFlags;

  /** Maps from long names to all registered flags. */
  private Map<String, Flag> mLongNames;

  /** Maps from short char names to flags (only for those that have short names). */
  private Map<Character, Flag> mShortNames;

  /** Custom text to tack on to the usage header. */
  private String mRemainderHeaderString = "";

  /** Typically a description of what the program does. */
  private String mProgramDescription = "";

  /** The name of the program accepting flags. */
  private String mProgramName;

  /** Optional validator for overall consistency between flags. */
  private Validator mValidator;

  /** Optional handler to deal with invalid flags. */
  private InvalidFlagHandler mInvalidFlagHandler = DEFAULT_INVALID_FLAG_HANDLER;

  // Set during setFlags()

  /** Stores all the read flags and their values, in the order they were seen. */
  private List<FlagValue> mReceivedFlags;

  /** The parse error string. */
  private String mParseMessageString = "";

  /**
   * Creates a new <code>CLIFlags</code> instance.
   * 
   * @param programName the name of the program.
   * @param programDescription a description of what the program does.
   */
  public CLIFlags(final String programName, final String programDescription) {
    this(programName);
    setDescription(programDescription);
  }

  /**
   * Creates a new <code>CLIFlags</code> instance.
   * 
   * @param programName the name of the program.
   */
  public CLIFlags(final String programName) {
    this();
    setName(programName);
  }

  /**
   * Creates a new <code>CLIFlags</code> instance.
   */
  public CLIFlags() {
    mAnonymousFlags = new ArrayList<Flag>();
    mRegisteredFlags = new TreeSet<Flag>();
    mLongNames = new TreeMap<String, Flag>();
    mShortNames = new TreeMap<Character, Flag>();
    registerOptional('h', HELP_FLAG, "Print help on command-line flag usage.");
  }

  // Switch flags -- those that have a name but don't take a parameter
  // These can only be optional

  /**
   * Registers an option. This option is not required to be specified, and has
   * no usage info and no type associated with it. This method is a shortcut for
   * simple boolean flags.
   * 
   * @param name the option name (without the prefix string).
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerOptional(final String name, final String description) {
    return registerOptional(name, null, "", description);
  }

  /**
   * Registers an option. This option is not required to be specified, and has
   * no usage info and no type associated with it. This method is a shortcut for
   * simple boolean flags.
   * 
   * @param nameChar single letter name.
   * @param name the option name (without the prefix string).
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerOptional(final char nameChar, final String name, final String description) {
    return registerOptional(nameChar, name, null, "", description, null);
  }

  // Required flags

  /**
   * Register an anonymous flag. An anonymous flag has no name. Any anonymous
   * flags are processed in the order they are encountered.
   * 
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerRequired(Class type, final String usage, final String description) {
    Flag flag = new AnonymousFlag(description, type, usage);
    register(flag);
    return flag;
  }

  /**
   * Registers a required flag. This flag requres a parameter of a specified
   * type.
   * 
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerRequired(final String name, Class type, final String usage,
      final String description) {
    return registerRequired(null, name, type, usage, description);
  }

  /**
   * Registers a required flag. This flag requres a parameter of a specified
   * type.
   * 
   * @param nameChar single letter name.
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerRequired(final char nameChar, final String name, Class type,
      final String usage, final String description) {
    return registerRequired(new Character(nameChar), name, type, usage, description);
  }

  // (currently) internal method that uses Character instead of (easier) char
  private Flag registerRequired(Character nameChar, final String name, Class type,
      final String usage, final String description) {
    return register(new Flag(nameChar, name, description, 1, 1, type, usage, null));
  }

  // Optional flags

  /**
   * Registers an option. When provided, this option requres a parameter of a
   * specified type.
   * 
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerOptional(final String name, Class type, final String usage,
      final String description) {
    return registerOptional(name, type, usage, description, null);
  }

  /**
   * Registers an option. This option requres a parameter of a specified type.
   * 
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerOptional(final String name, Class type, final String usage,
      final String description, Object defaultValue) {
    return registerOptional(null, name, type, usage, description, defaultValue);
  }

  /**
   * Registers an option. This option requres a parameter of a specified type.
   * 
   * @param nameChar single letter name.
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @return the flag.
   */
  public Flag registerOptional(final char nameChar, final String name, Class type,
      final String usage, final String description) {
    return registerOptional(nameChar, name, type, usage, description, null);
  }

  /**
   * Registers an option. This option requres a parameter of a specified type.
   * 
   * @param nameChar single letter name.
   * @param name the option name (without the prefix string).
   * @param type the <code>Class</code> of the expected parameter. Supported
   * classes are: <code>Boolean</code>, <code>Byte</code>,
   * <code>Character</code>, <code>Float</code>, <code>Double</code>,
   * <code>Integer</code>, <code>Long</code>, <code>Short</code>,
   * <code>File</code>, <code>URL</code> and <code>String</code>.
   * @param usage a one-word usage description of the expected parameter type.
   * For example this might be <code>FILE</code>, <code>DIR</code>.
   * @param description the option description.
   * @param defaultValue default value.
   * @return the flag.
   */
  public Flag registerOptional(final char nameChar, final String name, Class type,
      final String usage, final String description, Object defaultValue) {
    return registerOptional(new Character(nameChar), name, type, usage, description, defaultValue);
  }

  // (currently) internal method that uses Character instead of (easier) char
  private Flag registerOptional(Character nameChar, final String name, Class type,
      final String usage, final String description, Object defaultValue) {
    return register(new Flag(nameChar, name, description, 0, 1, type, usage, defaultValue));
  }

  /**
   * Register a flag.
   * 
   * @param flag flag to register
   * @return registered instance
   */
  public Flag register(Flag flag) {
    if (flag instanceof AnonymousFlag) {
      mAnonymousFlags.add(flag);
      mRegisteredFlags.add(flag);
    } else {
      if (mLongNames.containsKey(flag.getName())) {
        throw new IllegalArgumentException("A flag named " + flag.getName() + " already exists.");
      }
      if (flag.getChar() != null) {
        if (mShortNames.containsKey(flag.getChar())) {
          throw new IllegalArgumentException("A flag with short name " + flag.getChar()
              + " already exists.");
        }
        mShortNames.put(flag.getChar(), flag);
      }
      mRegisteredFlags.add(flag);
      mLongNames.put(flag.getName(), flag);
    }
    return flag;
  }

  /**
   * Returns a set of the required flags that have not been fully set during
   * <code>setFlags</code>.
   * 
   * @return a set of flags.
   */
  private Collection<Flag> getPendingRequired() {
    Collection<Flag> results = new ArrayList<Flag>();
    for (Flag f : mRegisteredFlags) {
      if (f.getCount() < f.getMinCount()) {
        results.add(f);
      }
    }
    return results;
  }

  /**
   * Returns a collection of the required flags.
   * 
   * @return a collection of flags.
   */
  public Collection<Flag> getRequired() {
    Collection<Flag> results = new ArrayList<Flag>();
    for (Flag f : mRegisteredFlags) {
      if (f.getMinCount() > 0) {
        results.add(f);
      }
    }
    return results;
  }

  /**
   * Returns a collection of the optional flags.
   * 
   * @return a collection of flags.
   */
  public Collection<Flag> getOptional() {
    Collection<Flag> results = new ArrayList<Flag>();
    for (Flag f : mRegisteredFlags) {
      if (f.getMinCount() == 0) {
        results.add(f);
      }
    }
    return results;
  }

  public String getParseMessage() {
    return mParseMessageString;
  }

  public void setParseMessage(final String parseString) {
    mParseMessageString = parseString;
  }

  private void setRemainingParseMessage(Collection remaining) {
    StringBuffer usage = new StringBuffer();
    if (remaining != null) {
      if (remaining.size() == 1) {
        usage.append("unexpected argument");
      } else {
        usage.append("unexpected arguments");
      }
      Iterator remItr = remaining.iterator();
      while (remItr.hasNext()) {
        final String s = (String) remItr.next();
        usage.append(' ').append(s);
      }
    }
    setParseMessage(usage.toString());
  }

  private void setPendingParseMessage(Collection pendingRequired) {
    StringBuffer usage = new StringBuffer();
    if ((pendingRequired != null) && !pendingRequired.isEmpty()) {
      if (pendingRequired.size() == 1) {
        usage.append("you must provide a value for");
      } else {
        usage.append("you must provide values for");
      }
      Iterator reqItr = pendingRequired.iterator();
      while (reqItr.hasNext()) {
        Flag f = (Flag) reqItr.next();
        usage.append(' ').append(f.getCompactFlagUsage());
        if (f.getMinCount() > 1) {
          final int count = f.getMinCount() - f.getCount();
          usage.append(" (").append(count).append((count == 1) ? " more time)" : " more times)");
        }
      }
    }
    setParseMessage(usage.toString());
  }

  /**
   * Sets the header text giving usage regarding standard input and output.
   * 
   * @param usageString a short description to append to the header text.
   */
  public void setRemainderHeader(final String usageString) {
    mRemainderHeaderString = usageString;
  }

  /**
   * Sets the name of the program reading the arguments. Used when printing
   * usage.
   * 
   * @param progName the name of the program.
   */
  public void setName(final String progName) {
    mProgramName = progName;
  }

  /**
   * Sets the description of the program reading the arguments. Used when
   * printing usage.
   * 
   * @param description the description.
   */
  public void setDescription(final String description) {
    mProgramDescription = description;
  }

  private void setFlag(Flag flag, final String strValue) {
    // System.err.println("Setting flag " + flag + " to " + strValue);
    mReceivedFlags.add(flag.setValue(strValue));
  }

  public void setInvalidFlagHandler(InvalidFlagHandler handler) {
    mInvalidFlagHandler = handler;
  }

  public void setValidator(Validator validator) {
    mValidator = validator;
  }

  /** Resets the list of flags received and their values. */
  private void reset() {
    mReceivedFlags = new ArrayList<FlagValue>();
    for (Flag f : mRegisteredFlags) {
      f.reset();
    }
    for (Flag f : mAnonymousFlags) {
      f.reset();
    }
    setParseMessage("");
  }

  /**
   * This method allows the setting of flags via a Properties instance. This
   * should be a convenient way of setting the values consistently. However,
   * since the Properties type has no fixed convention for expressing
   * multi-valued property values (e.g. names="joe","bob"), only single-valued
   * properties should be submitted to this method. Multi-valued properties will
   * likely cause an exception in the parsing of the value.
   * 
   * @param properties A Properties instance with single-valued properties only.
   * @return True if properties were successfully used to set flags.
   */
  public boolean setFlags(final Properties properties) {
    reset();
    boolean success = true;
    final Collection pendingRequired = getRequired();
    final Enumeration keys = properties.propertyNames();
    while (keys.hasMoreElements()) {
      final String name = (String) keys.nextElement();
      if (!mLongNames.containsKey(name)) {
        success = false;
        break;
      }
      try {
        final Flag flag = getFlag(name);
        pendingRequired.remove(flag);
        setFlag(flag, properties.getProperty(name));
      } catch (RuntimeException e) {
        return false;
      }
    }
    if (success && !pendingRequired.isEmpty()) {
      setPendingParseMessage(pendingRequired);
      success = false;
    }
    return success;
  }

  /**
   * Parses the command line flags for later use by the
   * <code>getValue(final String flagname)</code> method.
   * 
   * @param args The new flags value.
   * @return True iff all required flags were seen and all seen flags were of
   * set with expected type.
   */
  public boolean setFlags(String[] args) {
    reset();
    List<String> remaining = new ArrayList<String>();
    boolean success = true;
    int anonymousCount = 0;
    int i = 0;
    for (; i < args.length && success; i++) {
      final String nameArg = args[i];
      Flag flag = null;
      String value = null;

      if (nameArg.startsWith(SHORT_FLAG_PREFIX)) {
        if (nameArg.startsWith(LONG_FLAG_PREFIX)) {
          String name = nameArg.substring(LONG_FLAG_PREFIX.length());
          final int splitpos = name.indexOf('=');
          if (splitpos != -1) {
            value = name.substring(splitpos + 1);
            name = name.substring(0, splitpos);
          }
          flag = getFlag(name);
        } else if (nameArg.length() == SHORT_FLAG_PREFIX.length() + 1) {
          Character nameChar = new Character(nameArg.charAt(SHORT_FLAG_PREFIX.length()));
          flag = mShortNames.get(nameChar);
        }
        if (flag == null) {
          setParseMessage("Unknown flag " + nameArg);
          success = false;
        }
      }

      if (flag != null) {
        if ((flag.getParameterType() != null) && (value == null)) {
          i++;
          if (i == args.length) {
            setParseMessage("Expecting value for flag " + nameArg);
            success = false;
            break;
          } else {
            value = args[i];
          }
        }

        try {
          setFlag(flag, value);
        } catch (FlagCountException ie) {
          setParseMessage("Attempt to set flag " + nameArg + " too many times.");
          success = false;
        } catch (IllegalArgumentException e) {
          setParseMessage("Invalid value " + value + " for " + nameArg + ". " + e.getMessage());
          success = false;
        }
      } else if (anonymousCount < mAnonymousFlags.size()) {
        flag = getAnonymousFlag(anonymousCount);
        setFlag(flag, args[i]);
        if (flag.getCount() == flag.getMaxCount()) {
          anonymousCount++;
        }
      } else {
        remaining.add(args[i]);
      }
    }

    if (!success) {
      // Quickly scan unparsed args to see if it looks like they tried to ask
      // for help
      for (; i < args.length; i++) {
        if ((LONG_FLAG_PREFIX + HELP_FLAG).equals(args[i])
            || (SHORT_FLAG_PREFIX + "h").equals(args[i])) {
          setFlag(getFlag(HELP_FLAG), null);
        }
      }
    }

    if (isSet(HELP_FLAG)) {
      success = false;
    }

    if (success && !remaining.isEmpty()) {
      setRemainingParseMessage(remaining);
      success = false;
    }

    Collection pendingRequired = getPendingRequired();
    // System.err.println(pendingRequired);
    if (success && !pendingRequired.isEmpty()) {
      setPendingParseMessage(pendingRequired);
      success = false;
    }

    if (success && (mValidator != null) && (!mValidator.isValid(this))) {
      success = false;
    }

    if (!success && (mInvalidFlagHandler != null)) {
      mInvalidFlagHandler.handleInvalidFlags(this);
    }

    return success;
  }

  /**
   * Get an iterator over anonymous flags.
   * 
   * @return iterator over anonymous flags.
   */
  public Iterator<Flag> getAnonymousFlags() {
    return mAnonymousFlags.iterator();
  }

  /**
   * Get an anonymous flag by index.
   * 
   * @param index the index
   * @return the flag
   */
  public Flag getAnonymousFlag(final int index) {
    return mAnonymousFlags.get(index);
  }

  /**
   * Get a flag from its name.
   * 
   * @param flag flag name
   * @return the flag.
   */
  public Flag getFlag(final String flag) {
    return mLongNames.get(flag);
  }

  /**
   * Gets the value supplied with a flag.
   * 
   * @param flag the name of the flag (without the prefix).
   * @return an <code>Object</code> value. This object will be of type
   * configured during the option registering. You can also get the value of
   * no-type flags as a boolean, which indicates whether the no-type flag
   * occurred.
   */
  public Object getValue(final String flag) {
    return getFlag(flag).getValue();
  }

  /**
   * Get the values of a flag.
   * 
   * @param flag the flag
   * @return values of the flag
   */
  public Collection<Object> getValues(final String flag) {
    return getFlag(flag).getValues();
  }

  /**
   * Get an anonymous value.
   * 
   * @param index the index
   * @return the anonymous value
   */
  public Object getAnonymousValue(final int index) {
    return getAnonymousFlag(index).getValue();
  }

  /**
   * Get the anonymous values.
   * 
   * @param index the index
   * @return anonymous values
   */
  public Collection<Object> getAnonymousValues(final int index) {
    return getAnonymousFlag(index).getValues();
  }

  /**
   * Returns an iterator over the values that the user supplied, in the order
   * that they were supplied. Each element of the Iterator is a FlagValue.
   * 
   * @return an <code>Iterator</code> of <code>FlagValue</code>s.
   */
  public Iterator<FlagValue> getReceivedValues() {
    return mReceivedFlags.iterator();
  }

  /**
   * Returns true if a particular flag was provided in the arguments.
   * 
   * @param flag the name of the option.
   * @return true if the option was provided in the arguments.
   */
  public boolean isSet(final String flag) {
    final Flag aFlag = mLongNames.get(flag);
    return (aFlag == null) ? false : aFlag.isSet();
  }

  /**
   * Gets a compact description of the required and optional flags. This
   * contains only the names of the options with their usage parameters (i.e.:
   * not their individual descriptions).
   * 
   * @return a short <code>String</code> listing the options.
   */
  public String getCompactFlagUsage() {
    final WrappingStringBuffer sb = new WrappingStringBuffer();
    appendCompactFlagUsage(sb);
    return sb.toString().trim();
  }

  private void appendUsageHeader(WrappingStringBuffer wb) {
    if (mProgramName != null) {
      wb.append(USAGE_SUMMARY_PREFIX);
      wb.append(mProgramName);
      wb.append(' ');
      wb.setWrapIndent();
      appendCompactFlagUsage(wb);
      if (!mRemainderHeaderString.equals("")) {
        wb.append(' ');
        wb.wrapText(mRemainderHeaderString);
      }
      wb.append(LS);
    }
  }

  /**
   * Adds compact flag usage information to the given WrappingStringBuffer,
   * wrapping at appropriate places.
   */
  private void appendCompactFlagUsage(WrappingStringBuffer wb) {
    boolean first = true;
    if (getOptional().size() > 0) {
      wb.wrapWord("[OPTION]...");
      first = false;
    }
    Iterator it = getRequired().iterator();
    while (it.hasNext()) {
      wb.wrapWord((first ? "" : " ") + ((Flag) it.next()).getCompactFlagUsage());
      first = false;
    }
  }

  private void appendParseMessage(WrappingStringBuffer wb) {
    if (!mParseMessageString.equals("")) {
      wb.append(LS).append(PARSE_ERROR_PREFIX);
      wb.setWrapIndent(PARSE_ERROR_PREFIX.length());
      wb.wrapText(mParseMessageString).append(LS);
    }
  }

  /**
   * @return a <code>String</code> containing the full usage information. This
   * contains the usage header, usage for each flag and usage footer.
   */
  public String getUsageString() {
    return getUsageString(DEFAULT_WIDTH);
  }

  /**
   * Get the usage string.
   * 
   * @param width width of output
   * @return usage wrapped to given width
   */
  public String getUsageString(final int width) {
    final WrappingStringBuffer usage = new WrappingStringBuffer();
    usage.setWrapWidth(width);

    appendUsageHeader(usage);

    appendParseMessage(usage);

    appendLongFlagUsage(usage);

    if (!mProgramDescription.equals("")) {
      usage.append(LS);
      usage.setWrapIndent(0);
      usage.wrapText(mProgramDescription);
    }
    return usage.toString();
  }

  private void appendLongFlagUsage(WrappingStringBuffer wb) {
    int longestUsageLength = 0;
    // Get longest string lengths for use below in pretty-printing.
    final Iterator flagItr = mRegisteredFlags.iterator();
    while (flagItr.hasNext()) {
      Flag flag = (Flag) flagItr.next();
      final String usageStr = flag.getFlagUsage();
      if (usageStr.length() > longestUsageLength) {
        longestUsageLength = usageStr.length();
      }
    }

    // We do all the required flags first
    final Collection required = getRequired();
    if (required.size() > 0) {
      wb.append(LS);
      wb.append(REQUIRED_FLAG_USAGE_PREFIX).append(LS);
      wb.setWrapIndent(longestUsageLength + 7);
      for (Iterator flagItr2 = required.iterator(); flagItr2.hasNext();) {
        ((Flag) flagItr2.next()).appendLongFlagUsage(wb, longestUsageLength);
      }
    }

    // Then all the optional flags
    final Collection optional = getOptional();
    if (optional.size() > 0) {
      wb.setWrapIndent(0);
      wb.append(LS);
      wb.append(OPTIONAL_FLAG_USAGE_PREFIX).append(LS);
      wb.setWrapIndent(longestUsageLength + 7);
      for (Iterator flagItr2 = optional.iterator(); flagItr2.hasNext();) {
        ((Flag) flagItr2.next()).appendLongFlagUsage(wb, longestUsageLength);
      }
    }
  }

  /**
   * Prints the full usage information to System.err.
   */
  public void printUsage() {
    System.err.println(getUsageString());
  }

  /**
   * Just used for testing purposes
   * 
   * @param args The command line arguments.
   */
  public static void main(String[] args) {
    CLIFlags cli = new CLIFlags("CLIFlags");

    // cli.setRemainderHeader("ARGS");
    // Maybe support remaining args like this:
    cli.registerRequired(String.class, "ARGS", "These are some extra required args");
    cli.registerRequired(String.class, "BARGS", "These are some extra required args");

    Flag intFlag = cli.registerRequired('i', "int", Integer.class, "my_int",
        "This sets an int value.");
    intFlag.setMaxCount(5);
    // intFlag.setMaxCount(Integer.MAX_VALUE);
    intFlag.setMinCount(2);

    cli.registerOptional('s', "switch", "This is a toggle flag.");

    cli.registerOptional('b', "boolean", Boolean.class, "true/false", "This sets a boolean value.",
        Boolean.TRUE);

    Flag f = cli.registerOptional('f', "float", Float.class, null, "This sets a float value.",
        new Float(20));
    f.setParameterRange(new String[] {"0.2", "0.4", "0.6" });
    cli
        .registerOptional(
            "string",
            String.class,
            null,
            "This sets a string value. and for this one I'm going to have quite a long description. Possibly long enough to need wrapping.",
            "myDefault");
    cli.setFlags(args);

    System.out.println("--switch: " + cli.getValue("switch"));
    System.out.println("--boolean: " + cli.getValue("boolean"));
    System.out.println("--int: " + cli.getValue("int"));
    System.out.println("--float: " + cli.getValue("float"));
    System.out.println("--string: " + cli.getValue("string"));

    System.out.println(LS + "Multi-occurrence flag:");
    System.out.println("--int: " + cli.getValues("int"));

    System.out.println(LS + "Received values in order:");
    for (Iterator it = cli.getReceivedValues(); it.hasNext();) {
      System.out.println(it.next());
    }

  }
}

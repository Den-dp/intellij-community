// "Add constructor parameters from ArrayList((MutableCollection<out String!>..Collection<String!>?))" "true"
// ACTION: Add constructor parameters from ArrayList((MutableCollection<out String!>..Collection<String!>?))
// ACTION: Add constructor parameters from ArrayList(Int)
// ACTION: Change to constructor invocation
// RUNTIME_WITH_FULL_JDK
import java.util.ArrayList

class C : ArrayList<String><caret>

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SuperClassNotInitialized$AddParametersFix
// IGNORE_K2
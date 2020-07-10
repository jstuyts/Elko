package org.elkoserver.server.director

/**
 * Test if a user name is the name of a user clone.
 *
 * @param userName  The user name to test.
 *
 * @return true if 'userName' is the name of a clone.  It is assumed to
 * be a clone name if it contains more than one '-' character.
 */
internal fun isUserClone(userName: String) = userName.indexOf('-') != userName.lastIndexOf('-')

/**
 * Obtain the name of a user clone set from a user name.
 *
 * @param userName  The user name to get the clone set name from.
 *
 * @return the substring of 'userName' up to but not including the
 * second '-' character.
 */
internal fun userCloneSetName(userName: String): String {
    var dash = userName.indexOf('-')
    dash = userName.indexOf('-', dash + 1)
    return userName.take(dash)
}

/*
// $Id$
// This software is subject to the terms of the Common Public License
// Agreement, available at the following URL:
// http://www.opensource.org/licenses/cpl.html.
// Copyright (C) 2004-2005 TONBELLER AG
// All Rights Reserved.
// You must accept the terms of that agreement to use this software.
*/
package mondrian.olap;

/**
 * Abstract base class for exceptions that indicate some limit was exceeded.
 *
 * @version $Id$
 */
public abstract class ResultLimitExceeded extends MondrianException {
    public ResultLimitExceeded(String message) {
        super(message);
    }
}

// End ResultLimitExceeded.java
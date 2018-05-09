/*
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, write to the Free Software Foundation, Inc., 51
 * Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * Copyright (c) 2012-2014, Purdue University
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_asraw extends TestBase {

    @Test
    public void testasraw1() {
        assertEval("argv <- list(structure(numeric(0), .Dim = c(0L, 0L)));as.raw(argv[[1]]);");
    }

    @Test
    public void testasraw2() {
        assertEval("argv <- list(integer(0));as.raw(argv[[1]]);");
    }

    @Test
    public void testasraw3() {
        assertEval("argv <- list(logical(0));as.raw(argv[[1]]);");
    }

    @Test
    public void testasraw4() {
        assertEval("argv <- list(character(0));as.raw(argv[[1]]);");
    }

    @Test
    public void testasraw5() {
        assertEval("argv <- list(NULL);as.raw(argv[[1]]);");
    }

    @Test
    public void testasraw6() {
        assertEval("argv <- list(list());as.raw(argv[[1]]);");
    }

    @Test
    public void testAsRaw() {
        assertEval("{ as.raw() }");
        assertEval("{ as.raw(NULL) }");
        assertEval("{ as.raw(1) }");
        assertEval("{ as.raw(1L) }");
        assertEval("{ as.raw(1.1) }");
        assertEval("{ as.raw(c(1, 2, 3)) }");
        assertEval("{ as.raw(c(1L, 2L, 3L)) }");
        assertEval("{ as.raw(list(1,2,3)) }");
        assertEval("{ as.raw(list(\"1\", 2L, 3.4)) }");
        assertEval("{ as.raw.cls <- function(x) 42; as.raw(structure(c(1,2), class='cls')); }");

        assertEval(Output.IgnoreWarningContext, "{ as.raw(1+1i) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(-1) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(-1L) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(NA) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(\"test\") }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c(1+3i, -2-1i, NA)) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c(1, -2, 3)) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c(1,1000,NA)) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c(1L, -2L, 3L)) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c(1L, -2L, NA)) }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw('10000001') }");
        assertEval(Output.IgnoreWarningContext, "{ as.raw(c('10000001', '42')) }");
        assertEval("{ y <- as.raw(c(5L, 6L)); attr(y, 'someAttr') <- 'someValue'; x <- as.raw(y); x[[1]] <- as.raw(42); y }");
    }
}

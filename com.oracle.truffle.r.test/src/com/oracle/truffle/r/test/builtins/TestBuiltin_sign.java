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
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_sign extends TestBase {

    @Test
    public void testsign1() {
        assertEval("argv <- list(29);sign(argv[[1]]);");
    }

    @Test
    public void testsign2() {
        assertEval("argv <- list(-29);sign(argv[[1]]);");
    }

    @Test
    public void testsign3() {
        assertEval("argv <- list(structure(-29.5, .Names = 'W'));sign(argv[[1]]);");
    }

    @Test
    public void testsign4() {
        assertEval("argv <- list(c(2, 1.5));sign(argv[[1]]);");
    }

    @Test
    public void testsign5() {
        assertEval("argv <- list(structure(c(0.880860525591375, 0.639733585162877, 0.698114489497201, -0.163771828170666, 0.644716815673843, 0.434938037582636, -1.02532598809559, -0.414997803714266, 0.314897800466685, 0.824285322485286, 0.771220667991526, -1.0213685325144, 0.928795080183842, 0.819280413726459, -1.81676447087493, 0.750354069620072, 0.445075757764079, -0.708114061379466, 0.824862990562917, -0.538393491087728, 0.974198118249183, -1.44391305877857, -0.0570136982996023, -0.0628620473044737, 0.00599485749367468, 0.397443892596693, -0.670529694022941, -0.443694007369259, -1.60185734774623, -0.125754544304519, 0.726126214864875, -0.0167895964097286, -0.306643229540329, -0.216330373334122, -0.903891452322388, 0.326172148813803, -0.13510345952301, -0.897613228123322, 0.845413917001047, -0.831631251080141, 0.487109758044019, -2.39537135767952, -1.00899546383701, -0.15086268042785, 0.817762526779461, -0.0500097005975852, 0.489115737630558, -0.570402758036241, 0.837693310865448, 0.128079053272328, -0.543417844555625, -0.372441278809232, 0.0566412271335022, -0.292618377937407, 0.331718074329116, 0.424938499372394, 0.976537923557996, 0.463868773879129, -0.204612235294409, 0.635623103866607, 0.563790796039522, 0.102279312881195, -0.0139544456391161, 0.319200502078835, -0.348934065906413, 0.553375167400346, -0.448280809644608, -0.00983940055010783, -0.259698968965015, 0.919652420667434, -0.47355400612706, -0.135894354949879, -0.0129965646298911, 0.162878599329267, 0.243328472793848, -0.0718304876664265), .Names = c('1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '22', '23', '24', '25', '26', '27', '28', '29', '30', '31', '32', '33', '34', '35', '36', '37', '38', '39', '40', '41', '42', '43', '44', '45', '46', '47', '48', '49', '50', '51', '52', '53', '54', '55', '56', '57', '58', '59', '60', '61', '62', '63', '64', '65', '66', '67', '68', '69', '70', '71', '72', '73', '74', '75', '76')));sign(argv[[1]]);");
    }

    @Test
    public void testsign6() {
        assertEval("argv <- list(structure(c(-Inf, Inf, -Inf), .Dim = 3L, .Dimnames = list(c('73', '312', '674'))));sign(argv[[1]]);");
    }

    @Test
    public void testsign7() {
        assertEval("argv <- list(c(NA, 2L, 2L));sign(argv[[1]]);");
    }

    @Test
    public void testsign8() {
        assertEval("argv <- list(c(-2.3, -0.9, -0.0666666666666667, 0.275, 0.12, 0.216666666666667, -0.228571428571429, -0.35, -0.188888888888889, -1.77635683940025e-16, 0.0272727272727272, -0.108333333333333, -0.246153846153846));sign(argv[[1]]);");
    }

    @Test
    public void testsign9() {
        assertEval("argv <- list(numeric(0));sign(argv[[1]]);");
    }

    @Test
    public void testsign10() {
        assertEval("sign(c(FALSE))");
        assertEval("sign(c(FALSE, TRUE))");
        assertEval("sign(c(1, -1, FALSE))");
        assertEval("sign(list(c(1, -1, FALSE))[[1]])");
        assertEval("sign(1i)");
        assertEval("sign(c(1, -1, FALSE, 1i))");
        assertEval("sign('a')");
        assertEval("sign('1')");
        assertEval("sign('1L')");
        assertEval("sign(NULL)");
        assertEval("sign(NaN)");
        assertEval("sign(NA_real_)");
    }
}

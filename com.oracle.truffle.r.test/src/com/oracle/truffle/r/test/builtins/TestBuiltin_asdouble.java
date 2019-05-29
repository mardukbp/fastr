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
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.builtins;

import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;

// Checkstyle: stop line length check
public class TestBuiltin_asdouble extends TestBase {

    @Test
    public void testasdouble1() {
        assertEval("argv <- list(c(3.14159265358979, 3.14159265358981, 3.14159265358981, 3.14159265358981, 3.14159265358978, 3.1415926535898, 3.1415926535898, 3.14159265358978, 3.14159265358981, 3.14159265358979, 3.14159265358979, 3.14159265358978, 3.14159265358979, 3.14159265358981, 3.1415926535898, 3.14159265358978, 3.14159265358978, 3.14159265358978, 3.14159265358978, 3.14159265358981, 3.14159265358981, 3.14159265358978, 3.14159265358979, 3.14159265358978, 3.14159265358978, 3.14159265358978, 3.1415926535898, 3.14159265358977, 3.14159265358979, 3.14159265358979, 3.1415926535898, 3.14159265358979, 3.1415926535898, 3.14159265358979, 3.14159265358979, 3.14159265358981, 3.14159265358978, 3.1415926535898, 3.14159265358979, 3.14159265358981, 3.1415926535898, 3.14159265358981, 3.14159265358978, 3.1415926535898, 3.14159265358981, 3.1415926535898, 3.14159265358978, 3.14159265358979, 3.14159265358978, 3.14159265358979, 3.1415926535898, 3.14159265358981, 3.14159265358978, 3.1415926535898, 3.14159265358979, 3.1415926535898, 3.14159265358978, 3.1415926535898, 3.14159265358978, 3.14159265358979, 3.1415926535898, 3.14159265358978, 3.1415926535898, 3.14159265358981, 3.14159265358977, 3.14159265358981, 3.14159265358978, 3.14159265358978, 3.14159265358981, 3.14159265358979, 3.14159265358977, 3.14159265358978, 3.14159265358981, 3.14159265358979, 3.14159265358981, 3.1415926535898, 3.14159265358979, 3.14159265358979, 3.1415926535898, 3.14159265358979, 3.14159265358981, 3.14159265358979, 3.14159265358979, 3.14159265358981, 3.14159265358977, 3.1415926535898, 3.14159265358979, 3.1415926535898, 3.14159265358979, 3.1415926535898, 3.1415926535898, 3.1415926535898, 3.14159265358978, 3.1415926535898, 3.1415926535898, 3.1415926535898, 3.14159265358981, 3.14159265358979, 3.14159265358978, 3.14159265358981, 3.14159265358981));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble2() {
        assertEval("argv <- list(c('10', '2.7404', '0.27404', ''));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble3() {
        assertEval("argv <- list(structure(4, tzone = 'GMT', units = 'days', class = 'difftime'), units = 'secs');as.double(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testasdouble4() {
        assertEval("argv <- list('NaN');as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble5() {
        assertEval("argv <- list(structure(c(1.97479242156194, 1.71068206679967, 1.52241456554483), .Names = c('Bens of Jura', 'Knock Hill', 'Lairig Ghru')));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble6() {
        assertEval("argv <- list(c(856722023.658297, 302896976.260735, 107090252.958018, 37862122.0336249, 13386281.6212132, 4732765.25626924, 1673285.20557359, 591595.661165903, 209160.656540579, 73949.4659102332, 26145.0937553316, 9243.69976775411, 3268.16009484595, 1155.49552841673, 408.56675987247, 144.503039869403, 51.1642500846007, 18.1945635811076, 6.57944169516568, 2.52146555042134, 1.10249557516018, 0.395623281358704, -0.367112032460934, 0.27396220835345, -0.0693674921217567, 0.0814543296800769, 0.0923699793060252, 0.0504684833914985, -0.0498360425475413, 0.00273531641447061, -0.00392881268836618));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble7() {
        assertEval("argv <- list(c(1.90069420068965e+68, 1.85614668036113e+65, 1.81264324254072e+62, 1.77015941654582e+59, 1.72867130522891e+56, 1.68815557154536e+53, 1.64858942546521e+50, 1.60995061130567e+47, 1.57221739580551e+44, 1.53536855821758e+41, 1.49938338742443e+38, 1.46424170564914e+35, 1.42992399523352e+32, 1.39641192722393e+29, 1.36369045875401e+26, 1.33175605805513e+23, 1.30064886911081e+20, 127057845771019376, 124241617095379, 121963623349.57, 121618014.278689, 129184.542208039, 178.330555907964, 0.906701004569228, -0.0905266041439205, 0.141777480680994, 0.0921442943441807, 0.0658533118803105, -0.0402995417166551, 0.0244881559995369, -0.0194680918617461));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble8() {
        assertEval("argv <- list(c(3.69420518444359e+25, 2.30887824027777e+24, 1.44304890017492e+23, 9.01905562612606e+21, 5.63690976641081e+20, 35230686042118275072, 2201917878145066496, 137619867512235136, 8601241751556820, 537577617482832, 33598603095309.8, 2099913194115.17, 131244699796.888, 8202825028.58974, 512684387.219832, 32044730.0464007, 2003284.70114408, 125327.674230857, 7863.68742857025, 499.272560819512, 33.2784230289721, 2.7659432263306, 0.488936768533843, -0.282943224311172, 7.32218543045282e-05, -0.00636442868227041, -0.0483709204009262, -0.0704795507649514, 0.0349437746169591, -0.0264830837608839, 0.0200901469411759));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble9() {
        assertEval("argv <- list(c('-.1', ' 2.7 ', 'B'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble10() {
        assertEval("argv <- list(structure(c(1, 2, 3, 4, 5, 0, 7, 8, 9, 10, 11, 12), .Dim = c(4L, 3L)));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble11() {
        assertEval("argv <- list(c(NA, '0.0021'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble12() {
        assertEval("argv <- list(character(0));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble13() {
        assertEval("argv <- list(structure(c(21, 16.4, 18.7, 16.8, 17.8, 10.9, 14, 3.5, 4.3, 3.5, 2.7, 6, 14, 2.3), .Dim = c(7L, 2L), .Dimnames = list(c('L', 'NL', 'D', 'B', 'F', 'IRL', 'UK'), c('x', 'y'))));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble14() {
        assertEval("argv <- list(c(0, 0, 0, 0, 0, 1.75368801162502e-134, 0, 0, 0, 2.60477585273833e-251, 1.16485035372295e-260, 0, 1.53160350210786e-322, 0.333331382328728, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3.44161262707712e-123, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1.96881154539801e-173, 0, 8.23599653846971e-150, 0, 0, 0, 0, 6.51733217171342e-10, 0, 2.36840184577368e-67, 0, 9.43484083575241e-307, 0, 1.59959906013772e-89, 0, 8.73836857865035e-286, 7.09716190970993e-54, 0, 0, 0, 1.530425353017e-274, 8.57590058044552e-14, 0.333333106397154, 0, 0, 1.36895217898448e-199, 2.0226102635783e-177, 5.50445388209462e-42, 0, 0, 0, 0, 1.07846402051283e-44, 1.88605464411243e-186, 1.09156111051203e-26, 0, 3.07028772732371e-124, 0.333333209689785, 0, 0, 0, 0, 0, 0, 3.09816093866831e-94, 0, 0, 4.75227273320951e-272, 0, 0, 2.30093251441394e-06, 0, 0, 1.27082826644707e-274, 0, 0, 0, 0, 0, 0, 0, 4.5662025456054e-65, 0, 2.77995853978268e-149, 0, 0, 0));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble15() {
        assertEval("argv <- list(structure(c(1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 2L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 3L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 4L, 5L, 5L, 5L, 5L, 5L, 5L, 5L, 5L, 5L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L, 6L), .Label = c('WinF', 'WinNF', 'Veh', 'Con', 'Tabl', 'Head'), class = 'factor'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble16() {
        assertEval("argv <- list(structure(28, units = 'days', class = 'difftime'), units = 'secs');as.double(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testasdouble17() {
        assertEval("argv <- list(structure(c(0, 0.000202660677936876, 0.00241803309686265, 0.00307283986031733, 0.00265711006681184, 0.00284334291579362, 0.00328411981940272, 0.00355926216704063, 0.00344761438149018, 0.00289210744006633, 0.00204225708046235, 0.00178504641867207, 0.00172572372502035, 0.00159946455058003, 0.00194868716238274, 0.00230753595074067, 0.00246954915831488, 0.00290833971278575, 0.00337412960419987, 0.00358181270769781, 0.00354428559372645, 0.00326334045783046, 0.00298117073292367, 0.00293436142844913, 0.0029459867318606, 0.00298412396438805, 0.00320781989229225, 0.00342867445796099, 0.00405369787195761, 0.00417753179826535, 0.00414267894375602, 0.00407024400729904, 0.00393965520892809, 0.00385238230694322, 0.00383595140804293, 0.00378781523717584, 0.0037736404476557, 0.00382248725149436, 0.00381326514145668, 0.0038973026728862, 0.00395676065396717, 0.00431861015154494, 0.00443079015716877, 0.00450544753584657, 0.00439372971759073, 0.00433442297069524, 0.00429954456230782, 0.00426944313801568, 0.00423795462806802, 0.00417472474765742, 0.0042795282659813, 0.00454163385850258, 0.00473601380444899, 0.00466407336984038, 0.00462392764582444, 0.00456056187379283, 0.0045055003087985, 0.00442670076624794, 0.00431121205766447, 0.00421990442925801, 0.00416971729251421, 0.00407853686842565, 0.00409133004830999, 0.0041364805798209, 0.00427208054940612, 0.0044573146303062, 0.00463786827882152, 0.00462599613024964, 0.00456902544608922, 0.00448500474247415, 0.00443631355776013, 0.0043987926301962, 0.00439976139365821, 0.00444739366229557, 0.00441357461857941, 0.00445091952164202, 0.00450346393728121, 0.00462169457996522, 0.004734024297345, 0.00475873200245829, 0.00475253573403064, 0.00471631526131182, 0.00465515282727091, 0.00464698887217466, 0.00462685789718263, 0.00462996361305565, 0.00464191147874474, 0.00464879307071608, 0.00469113571839472, 0.00476270873398438, 0.00477314235918783, 0.00479544142345609, 0.0047737904084596, 0.00471999826644103, 0.00469372169840419, 0.0046463488677134, 0.00461799759453906, 0.00458947682120691, 0.00460912357592989, 0.00463333675159159, 0.00466732307616235, 0.00471231441093801, 0.00474022677208645, 0.00477297287765633, 0.00476766819213148, 0.00473849505147981, 0.00469782534032621, 0.00463861048753855, 0.00457840111456501, 0.00452291229235016, 0.00446341204452307, 0.00442002128896248, 0.00442991931450486, 0.00446688166198173, 0.00452411449686222, 0.00458536543416883, 0.00454175859707822, 0.00450829288322652, 0.00445725707512455, 0.00439091360820385, 0.00437267387139272, 0.00436951404759565, 0.00439586780117785, 0.00443132731253063, 0.00447997483459774, 0.00446916178054371, 0.00448357738281654, 0.00448976052744213, 0.00450610513067692, 0.00449385388080097, 0.00448875792730345, 0.00450025038413588, 0.00448200635475038, 0.00445933490412089, 0.00437269614488144, 0.00441152247400175, 0.00444283816260407, 0.00446748686328766, 0.00448539598299297, 0.00445924890176085, 0.00444386385593038, 0.00445984197910477, 0.00443574296742794, 0.00440036042966077), .Tsp = c(1949, 1960.91666666667, 12), class = 'ts'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble18() {
        assertEval("argv <- list(c(0.0099, 0.099, 0.99, 9.9, 99, 990, 9900, 99000, 990000, 9900000, 9.9e+07, 9.9e+08, 9.9e+09, 9.9e+10));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble19() {
        assertEval("argv <- list(structure(180.958333333333, units = 'days', class = 'difftime'), units = 'secs');as.double(argv[[1]],argv[[2]]);");
    }

    @Test
    public void testasdouble20() {
        assertEval("argv <- list(NULL);as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble21() {
        assertEval("argv <- list(structure(list(foo = 5L, Species = 2L), .Names = c('foo', 'Species'), out.attrs = structure(list(dim = structure(c(6L, 3L), .Names = c('foo', 'Species')), dimnames = structure(list(foo = c('foo=1', 'foo=2', 'foo=3', 'foo=4', 'foo=5', 'foo=6'), Species = c('Species=1', 'Species=2', 'Species=3')), .Names = c('foo', 'Species'))), .Names = c('dim', 'dimnames')), row.names = 11L, class = 'data.frame'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble22() {
        assertEval("argv <- list(c(TRUE, FALSE, TRUE));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble23() {
        assertEval("argv <- list(c(TRUE, TRUE, FALSE));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble24() {
        assertEval("argv <- list(c(NA, NA, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble25() {
        assertEval("argv <- list(structure(c(1L, 1L, 1L, 1L, 1L, 2L, 2L, 2L, 2L, 2L, 3L, 3L, 3L, 3L, 3L, 4L, 4L, 4L, 4L, 4L), .Label = c('Rural Male', 'Rural Female', 'Urban Male', 'Urban Female'), class = 'factor', .Dim = c(5L, 4L)));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble26() {
        assertEval("argv <- list(c(NaN, 9.51350769866873, 4.5908437119988, 2.99156898768759, 2.21815954375769, 1.77245385090552, 1.48919224881282, 1.29805533264756, 1.1642297137253, 1.06862870211932, 1));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble27() {
        assertEval("argv <- list(structure(3:5, .Tsp = c(1, 3, 1), class = 'ts'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble28() {
        assertEval("argv <- list('Inf');as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble29() {
        assertEval("argv <- list(c(' 9', ' 3', ' 3', '  6.761', '156.678', ' 18.327', ' 11.764', '191.64', '323.56', '197.21', '190.64'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble30() {
        assertEval("argv <- list(structure(c(NA, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L, 17L, 18L, 19L, 20L, 21L, 22L, 23L, 24L, 25L, 26L, 27L, 28L, 29L, 30L, 31L, 32L, 33L, 34L, 35L, 36L, 37L, 38L, 39L, 40L, 41L, 42L, 43L, 44L, 45L, 46L, 47L, 48L, 49L, 50L, 51L, 52L, 53L, 54L, 55L, 56L, 57L, 58L, 59L, 60L, 61L, 62L, 63L, 64L, 65L, 66L, 67L, 68L, 69L, 70L, 71L, 72L, 73L, 74L, 75L, 76L, 77L, 78L, 79L, 80L, 81L, 82L, 83L, 84L, 85L, 86L, 87L, 88L, 89L, 90L, 91L, 92L, 93L, 94L, 95L, 96L, 97L, 98L, 99L, 100L), .Tsp = c(1, 101, 1), class = 'ts'));as.double(argv[[1]]);");
    }

    @Test
    public void testasdouble32() {
        assertEval("argv <- list(NA);do.call('as.double', argv)");
    }

    @Test
    public void testAsDouble() {
        assertEval("{ as.double() }");
        assertEval("{ as.double(\"1.27\") }");
        assertEval("{ as.double(1L) }");
        assertEval("{ as.double(as.raw(1)) }");
        assertEval("{ as.double(c('1','hello')) }");
        assertEval("{ as.double('TRUE') }");
        assertEval("{ as.double(10+2i) }");
        assertEval("{ as.double(c(3+3i, 4+4i)) }");
        assertEval("{ x<-c(a=1.1, b=2.2); dim(x)<-c(1,2); attr(x, \"foo\")<-\"foo\"; y<-as.double(x); attributes(y) }");
        assertEval("{ x<-c(a=1L, b=2L); dim(x)<-c(1,2); attr(x, \"foo\")<-\"foo\"; y<-as.double(x); attributes(y) }");
        assertEval("{ as.double(NULL) }");
        assertEval("{ as.double.cls <- function(x) 42; as.double(structure(c(1,2), class='cls')); }");
        assertEval("{ y <- c(3.1, 3.2); attr(y, 'someAttr') <- 'someValue'; x <- as.double(y); x[[1]] <- 42; y }");

        assertEval("{ f <- function() as.double('aaa'); f() }");
        assertEval("{ f <- function() as.numeric('aaa'); f() }");
        assertEval("{ f1 <- function() {f<- function() as.double('aaa'); f()}; f1() }");

        assertEval("as.double('\t\n Inf\t\n ')");
        assertEval("as.double('\t\n +Inf\t\n ')");
        assertEval("as.double('\t\n -Inf\t\n ')");
        assertEval("as.double('\t\n NaN\t\n ')");
        assertEval("as.double('\t\n +NaN\t\n ')");
        assertEval("as.double('\t\n -NaN\t\n ')");
        assertEval("as.double('- Inf')");
    }

    @Test
    public void noCopyCheck() {
        assertEvalFastR("{ x <- c(1, 3.5); .fastr.identity(x) == .fastr.identity(as.double(x)); }", "[1] TRUE");
    }
}

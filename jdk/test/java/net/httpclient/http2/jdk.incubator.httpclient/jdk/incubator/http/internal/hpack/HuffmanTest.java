/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package jdk.incubator.http.internal.hpack;

import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Integer.parseInt;
import static org.testng.Assert.*;

public final class HuffmanTest {

    //
    // https://tools.ietf.org/html/rfc7541#appendix-B
    //
    private static final String SPEC =
            // @formatter:off
     "                          code as bits                 as hex   len\n" +
     "        sym              aligned to MSB                aligned   in\n" +
     "                                                       to LSB   bits\n" +
     "       (  0)  |11111111|11000                             1ff8  [13]\n" +
     "       (  1)  |11111111|11111111|1011000                7fffd8  [23]\n" +
     "       (  2)  |11111111|11111111|11111110|0010         fffffe2  [28]\n" +
     "       (  3)  |11111111|11111111|11111110|0011         fffffe3  [28]\n" +
     "       (  4)  |11111111|11111111|11111110|0100         fffffe4  [28]\n" +
     "       (  5)  |11111111|11111111|11111110|0101         fffffe5  [28]\n" +
     "       (  6)  |11111111|11111111|11111110|0110         fffffe6  [28]\n" +
     "       (  7)  |11111111|11111111|11111110|0111         fffffe7  [28]\n" +
     "       (  8)  |11111111|11111111|11111110|1000         fffffe8  [28]\n" +
     "       (  9)  |11111111|11111111|11101010               ffffea  [24]\n" +
     "       ( 10)  |11111111|11111111|11111111|111100      3ffffffc  [30]\n" +
     "       ( 11)  |11111111|11111111|11111110|1001         fffffe9  [28]\n" +
     "       ( 12)  |11111111|11111111|11111110|1010         fffffea  [28]\n" +
     "       ( 13)  |11111111|11111111|11111111|111101      3ffffffd  [30]\n" +
     "       ( 14)  |11111111|11111111|11111110|1011         fffffeb  [28]\n" +
     "       ( 15)  |11111111|11111111|11111110|1100         fffffec  [28]\n" +
     "       ( 16)  |11111111|11111111|11111110|1101         fffffed  [28]\n" +
     "       ( 17)  |11111111|11111111|11111110|1110         fffffee  [28]\n" +
     "       ( 18)  |11111111|11111111|11111110|1111         fffffef  [28]\n" +
     "       ( 19)  |11111111|11111111|11111111|0000         ffffff0  [28]\n" +
     "       ( 20)  |11111111|11111111|11111111|0001         ffffff1  [28]\n" +
     "       ( 21)  |11111111|11111111|11111111|0010         ffffff2  [28]\n" +
     "       ( 22)  |11111111|11111111|11111111|111110      3ffffffe  [30]\n" +
     "       ( 23)  |11111111|11111111|11111111|0011         ffffff3  [28]\n" +
     "       ( 24)  |11111111|11111111|11111111|0100         ffffff4  [28]\n" +
     "       ( 25)  |11111111|11111111|11111111|0101         ffffff5  [28]\n" +
     "       ( 26)  |11111111|11111111|11111111|0110         ffffff6  [28]\n" +
     "       ( 27)  |11111111|11111111|11111111|0111         ffffff7  [28]\n" +
     "       ( 28)  |11111111|11111111|11111111|1000         ffffff8  [28]\n" +
     "       ( 29)  |11111111|11111111|11111111|1001         ffffff9  [28]\n" +
     "       ( 30)  |11111111|11111111|11111111|1010         ffffffa  [28]\n" +
     "       ( 31)  |11111111|11111111|11111111|1011         ffffffb  [28]\n" +
     "   ' ' ( 32)  |010100                                       14  [ 6]\n" +
     "   '!' ( 33)  |11111110|00                                 3f8  [10]\n" +
     "  '\"' ( 34)  |11111110|01                                 3f9  [10]\n" +
     "   '#' ( 35)  |11111111|1010                               ffa  [12]\n" +
     "   '$' ( 36)  |11111111|11001                             1ff9  [13]\n" +
     "   '%' ( 37)  |010101                                       15  [ 6]\n" +
     "   '&' ( 38)  |11111000                                     f8  [ 8]\n" +
     "   ''' ( 39)  |11111111|010                                7fa  [11]\n" +
     "   '(' ( 40)  |11111110|10                                 3fa  [10]\n" +
     "   ')' ( 41)  |11111110|11                                 3fb  [10]\n" +
     "   '*' ( 42)  |11111001                                     f9  [ 8]\n" +
     "   '+' ( 43)  |11111111|011                                7fb  [11]\n" +
     "   ',' ( 44)  |11111010                                     fa  [ 8]\n" +
     "   '-' ( 45)  |010110                                       16  [ 6]\n" +
     "   '.' ( 46)  |010111                                       17  [ 6]\n" +
     "   '/' ( 47)  |011000                                       18  [ 6]\n" +
     "   '0' ( 48)  |00000                                         0  [ 5]\n" +
     "   '1' ( 49)  |00001                                         1  [ 5]\n" +
     "   '2' ( 50)  |00010                                         2  [ 5]\n" +
     "   '3' ( 51)  |011001                                       19  [ 6]\n" +
     "   '4' ( 52)  |011010                                       1a  [ 6]\n" +
     "   '5' ( 53)  |011011                                       1b  [ 6]\n" +
     "   '6' ( 54)  |011100                                       1c  [ 6]\n" +
     "   '7' ( 55)  |011101                                       1d  [ 6]\n" +
     "   '8' ( 56)  |011110                                       1e  [ 6]\n" +
     "   '9' ( 57)  |011111                                       1f  [ 6]\n" +
     "   ':' ( 58)  |1011100                                      5c  [ 7]\n" +
     "   ';' ( 59)  |11111011                                     fb  [ 8]\n" +
     "   '<' ( 60)  |11111111|1111100                           7ffc  [15]\n" +
     "   '=' ( 61)  |100000                                       20  [ 6]\n" +
     "   '>' ( 62)  |11111111|1011                               ffb  [12]\n" +
     "   '?' ( 63)  |11111111|00                                 3fc  [10]\n" +
     "   '@' ( 64)  |11111111|11010                             1ffa  [13]\n" +
     "   'A' ( 65)  |100001                                       21  [ 6]\n" +
     "   'B' ( 66)  |1011101                                      5d  [ 7]\n" +
     "   'C' ( 67)  |1011110                                      5e  [ 7]\n" +
     "   'D' ( 68)  |1011111                                      5f  [ 7]\n" +
     "   'E' ( 69)  |1100000                                      60  [ 7]\n" +
     "   'F' ( 70)  |1100001                                      61  [ 7]\n" +
     "   'G' ( 71)  |1100010                                      62  [ 7]\n" +
     "   'H' ( 72)  |1100011                                      63  [ 7]\n" +
     "   'I' ( 73)  |1100100                                      64  [ 7]\n" +
     "   'J' ( 74)  |1100101                                      65  [ 7]\n" +
     "   'K' ( 75)  |1100110                                      66  [ 7]\n" +
     "   'L' ( 76)  |1100111                                      67  [ 7]\n" +
     "   'M' ( 77)  |1101000                                      68  [ 7]\n" +
     "   'N' ( 78)  |1101001                                      69  [ 7]\n" +
     "   'O' ( 79)  |1101010                                      6a  [ 7]\n" +
     "   'P' ( 80)  |1101011                                      6b  [ 7]\n" +
     "   'Q' ( 81)  |1101100                                      6c  [ 7]\n" +
     "   'R' ( 82)  |1101101                                      6d  [ 7]\n" +
     "   'S' ( 83)  |1101110                                      6e  [ 7]\n" +
     "   'T' ( 84)  |1101111                                      6f  [ 7]\n" +
     "   'U' ( 85)  |1110000                                      70  [ 7]\n" +
     "   'V' ( 86)  |1110001                                      71  [ 7]\n" +
     "   'W' ( 87)  |1110010                                      72  [ 7]\n" +
     "   'X' ( 88)  |11111100                                     fc  [ 8]\n" +
     "   'Y' ( 89)  |1110011                                      73  [ 7]\n" +
     "   'Z' ( 90)  |11111101                                     fd  [ 8]\n" +
     "   '[' ( 91)  |11111111|11011                             1ffb  [13]\n" +
     "  '\\' ( 92)  |11111111|11111110|000                     7fff0  [19]\n" +
     "   ']' ( 93)  |11111111|11100                             1ffc  [13]\n" +
     "   '^' ( 94)  |11111111|111100                            3ffc  [14]\n" +
     "   '_' ( 95)  |100010                                       22  [ 6]\n" +
     "   '`' ( 96)  |11111111|1111101                           7ffd  [15]\n" +
     "   'a' ( 97)  |00011                                         3  [ 5]\n" +
     "   'b' ( 98)  |100011                                       23  [ 6]\n" +
     "   'c' ( 99)  |00100                                         4  [ 5]\n" +
     "   'd' (100)  |100100                                       24  [ 6]\n" +
     "   'e' (101)  |00101                                         5  [ 5]\n" +
     "   'f' (102)  |100101                                       25  [ 6]\n" +
     "   'g' (103)  |100110                                       26  [ 6]\n" +
     "   'h' (104)  |100111                                       27  [ 6]\n" +
     "   'i' (105)  |00110                                         6  [ 5]\n" +
     "   'j' (106)  |1110100                                      74  [ 7]\n" +
     "   'k' (107)  |1110101                                      75  [ 7]\n" +
     "   'l' (108)  |101000                                       28  [ 6]\n" +
     "   'm' (109)  |101001                                       29  [ 6]\n" +
     "   'n' (110)  |101010                                       2a  [ 6]\n" +
     "   'o' (111)  |00111                                         7  [ 5]\n" +
     "   'p' (112)  |101011                                       2b  [ 6]\n" +
     "   'q' (113)  |1110110                                      76  [ 7]\n" +
     "   'r' (114)  |101100                                       2c  [ 6]\n" +
     "   's' (115)  |01000                                         8  [ 5]\n" +
     "   't' (116)  |01001                                         9  [ 5]\n" +
     "   'u' (117)  |101101                                       2d  [ 6]\n" +
     "   'v' (118)  |1110111                                      77  [ 7]\n" +
     "   'w' (119)  |1111000                                      78  [ 7]\n" +
     "   'x' (120)  |1111001                                      79  [ 7]\n" +
     "   'y' (121)  |1111010                                      7a  [ 7]\n" +
     "   'z' (122)  |1111011                                      7b  [ 7]\n" +
     "   '{' (123)  |11111111|1111110                           7ffe  [15]\n" +
     "   '|' (124)  |11111111|100                                7fc  [11]\n" +
     "   '}' (125)  |11111111|111101                            3ffd  [14]\n" +
     "   '~' (126)  |11111111|11101                             1ffd  [13]\n" +
     "       (127)  |11111111|11111111|11111111|1100         ffffffc  [28]\n" +
     "       (128)  |11111111|11111110|0110                    fffe6  [20]\n" +
     "       (129)  |11111111|11111111|010010                 3fffd2  [22]\n" +
     "       (130)  |11111111|11111110|0111                    fffe7  [20]\n" +
     "       (131)  |11111111|11111110|1000                    fffe8  [20]\n" +
     "       (132)  |11111111|11111111|010011                 3fffd3  [22]\n" +
     "       (133)  |11111111|11111111|010100                 3fffd4  [22]\n" +
     "       (134)  |11111111|11111111|010101                 3fffd5  [22]\n" +
     "       (135)  |11111111|11111111|1011001                7fffd9  [23]\n" +
     "       (136)  |11111111|11111111|010110                 3fffd6  [22]\n" +
     "       (137)  |11111111|11111111|1011010                7fffda  [23]\n" +
     "       (138)  |11111111|11111111|1011011                7fffdb  [23]\n" +
     "       (139)  |11111111|11111111|1011100                7fffdc  [23]\n" +
     "       (140)  |11111111|11111111|1011101                7fffdd  [23]\n" +
     "       (141)  |11111111|11111111|1011110                7fffde  [23]\n" +
     "       (142)  |11111111|11111111|11101011               ffffeb  [24]\n" +
     "       (143)  |11111111|11111111|1011111                7fffdf  [23]\n" +
     "       (144)  |11111111|11111111|11101100               ffffec  [24]\n" +
     "       (145)  |11111111|11111111|11101101               ffffed  [24]\n" +
     "       (146)  |11111111|11111111|010111                 3fffd7  [22]\n" +
     "       (147)  |11111111|11111111|1100000                7fffe0  [23]\n" +
     "       (148)  |11111111|11111111|11101110               ffffee  [24]\n" +
     "       (149)  |11111111|11111111|1100001                7fffe1  [23]\n" +
     "       (150)  |11111111|11111111|1100010                7fffe2  [23]\n" +
     "       (151)  |11111111|11111111|1100011                7fffe3  [23]\n" +
     "       (152)  |11111111|11111111|1100100                7fffe4  [23]\n" +
     "       (153)  |11111111|11111110|11100                  1fffdc  [21]\n" +
     "       (154)  |11111111|11111111|011000                 3fffd8  [22]\n" +
     "       (155)  |11111111|11111111|1100101                7fffe5  [23]\n" +
     "       (156)  |11111111|11111111|011001                 3fffd9  [22]\n" +
     "       (157)  |11111111|11111111|1100110                7fffe6  [23]\n" +
     "       (158)  |11111111|11111111|1100111                7fffe7  [23]\n" +
     "       (159)  |11111111|11111111|11101111               ffffef  [24]\n" +
     "       (160)  |11111111|11111111|011010                 3fffda  [22]\n" +
     "       (161)  |11111111|11111110|11101                  1fffdd  [21]\n" +
     "       (162)  |11111111|11111110|1001                    fffe9  [20]\n" +
     "       (163)  |11111111|11111111|011011                 3fffdb  [22]\n" +
     "       (164)  |11111111|11111111|011100                 3fffdc  [22]\n" +
     "       (165)  |11111111|11111111|1101000                7fffe8  [23]\n" +
     "       (166)  |11111111|11111111|1101001                7fffe9  [23]\n" +
     "       (167)  |11111111|11111110|11110                  1fffde  [21]\n" +
     "       (168)  |11111111|11111111|1101010                7fffea  [23]\n" +
     "       (169)  |11111111|11111111|011101                 3fffdd  [22]\n" +
     "       (170)  |11111111|11111111|011110                 3fffde  [22]\n" +
     "       (171)  |11111111|11111111|11110000               fffff0  [24]\n" +
     "       (172)  |11111111|11111110|11111                  1fffdf  [21]\n" +
     "       (173)  |11111111|11111111|011111                 3fffdf  [22]\n" +
     "       (174)  |11111111|11111111|1101011                7fffeb  [23]\n" +
     "       (175)  |11111111|11111111|1101100                7fffec  [23]\n" +
     "       (176)  |11111111|11111111|00000                  1fffe0  [21]\n" +
     "       (177)  |11111111|11111111|00001                  1fffe1  [21]\n" +
     "       (178)  |11111111|11111111|100000                 3fffe0  [22]\n" +
     "       (179)  |11111111|11111111|00010                  1fffe2  [21]\n" +
     "       (180)  |11111111|11111111|1101101                7fffed  [23]\n" +
     "       (181)  |11111111|11111111|100001                 3fffe1  [22]\n" +
     "       (182)  |11111111|11111111|1101110                7fffee  [23]\n" +
     "       (183)  |11111111|11111111|1101111                7fffef  [23]\n" +
     "       (184)  |11111111|11111110|1010                    fffea  [20]\n" +
     "       (185)  |11111111|11111111|100010                 3fffe2  [22]\n" +
     "       (186)  |11111111|11111111|100011                 3fffe3  [22]\n" +
     "       (187)  |11111111|11111111|100100                 3fffe4  [22]\n" +
     "       (188)  |11111111|11111111|1110000                7ffff0  [23]\n" +
     "       (189)  |11111111|11111111|100101                 3fffe5  [22]\n" +
     "       (190)  |11111111|11111111|100110                 3fffe6  [22]\n" +
     "       (191)  |11111111|11111111|1110001                7ffff1  [23]\n" +
     "       (192)  |11111111|11111111|11111000|00           3ffffe0  [26]\n" +
     "       (193)  |11111111|11111111|11111000|01           3ffffe1  [26]\n" +
     "       (194)  |11111111|11111110|1011                    fffeb  [20]\n" +
     "       (195)  |11111111|11111110|001                     7fff1  [19]\n" +
     "       (196)  |11111111|11111111|100111                 3fffe7  [22]\n" +
     "       (197)  |11111111|11111111|1110010                7ffff2  [23]\n" +
     "       (198)  |11111111|11111111|101000                 3fffe8  [22]\n" +
     "       (199)  |11111111|11111111|11110110|0            1ffffec  [25]\n" +
     "       (200)  |11111111|11111111|11111000|10           3ffffe2  [26]\n" +
     "       (201)  |11111111|11111111|11111000|11           3ffffe3  [26]\n" +
     "       (202)  |11111111|11111111|11111001|00           3ffffe4  [26]\n" +
     "       (203)  |11111111|11111111|11111011|110          7ffffde  [27]\n" +
     "       (204)  |11111111|11111111|11111011|111          7ffffdf  [27]\n" +
     "       (205)  |11111111|11111111|11111001|01           3ffffe5  [26]\n" +
     "       (206)  |11111111|11111111|11110001               fffff1  [24]\n" +
     "       (207)  |11111111|11111111|11110110|1            1ffffed  [25]\n" +
     "       (208)  |11111111|11111110|010                     7fff2  [19]\n" +
     "       (209)  |11111111|11111111|00011                  1fffe3  [21]\n" +
     "       (210)  |11111111|11111111|11111001|10           3ffffe6  [26]\n" +
     "       (211)  |11111111|11111111|11111100|000          7ffffe0  [27]\n" +
     "       (212)  |11111111|11111111|11111100|001          7ffffe1  [27]\n" +
     "       (213)  |11111111|11111111|11111001|11           3ffffe7  [26]\n" +
     "       (214)  |11111111|11111111|11111100|010          7ffffe2  [27]\n" +
     "       (215)  |11111111|11111111|11110010               fffff2  [24]\n" +
     "       (216)  |11111111|11111111|00100                  1fffe4  [21]\n" +
     "       (217)  |11111111|11111111|00101                  1fffe5  [21]\n" +
     "       (218)  |11111111|11111111|11111010|00           3ffffe8  [26]\n" +
     "       (219)  |11111111|11111111|11111010|01           3ffffe9  [26]\n" +
     "       (220)  |11111111|11111111|11111111|1101         ffffffd  [28]\n" +
     "       (221)  |11111111|11111111|11111100|011          7ffffe3  [27]\n" +
     "       (222)  |11111111|11111111|11111100|100          7ffffe4  [27]\n" +
     "       (223)  |11111111|11111111|11111100|101          7ffffe5  [27]\n" +
     "       (224)  |11111111|11111110|1100                    fffec  [20]\n" +
     "       (225)  |11111111|11111111|11110011               fffff3  [24]\n" +
     "       (226)  |11111111|11111110|1101                    fffed  [20]\n" +
     "       (227)  |11111111|11111111|00110                  1fffe6  [21]\n" +
     "       (228)  |11111111|11111111|101001                 3fffe9  [22]\n" +
     "       (229)  |11111111|11111111|00111                  1fffe7  [21]\n" +
     "       (230)  |11111111|11111111|01000                  1fffe8  [21]\n" +
     "       (231)  |11111111|11111111|1110011                7ffff3  [23]\n" +
     "       (232)  |11111111|11111111|101010                 3fffea  [22]\n" +
     "       (233)  |11111111|11111111|101011                 3fffeb  [22]\n" +
     "       (234)  |11111111|11111111|11110111|0            1ffffee  [25]\n" +
     "       (235)  |11111111|11111111|11110111|1            1ffffef  [25]\n" +
     "       (236)  |11111111|11111111|11110100               fffff4  [24]\n" +
     "       (237)  |11111111|11111111|11110101               fffff5  [24]\n" +
     "       (238)  |11111111|11111111|11111010|10           3ffffea  [26]\n" +
     "       (239)  |11111111|11111111|1110100                7ffff4  [23]\n" +
     "       (240)  |11111111|11111111|11111010|11           3ffffeb  [26]\n" +
     "       (241)  |11111111|11111111|11111100|110          7ffffe6  [27]\n" +
     "       (242)  |11111111|11111111|11111011|00           3ffffec  [26]\n" +
     "       (243)  |11111111|11111111|11111011|01           3ffffed  [26]\n" +
     "       (244)  |11111111|11111111|11111100|111          7ffffe7  [27]\n" +
     "       (245)  |11111111|11111111|11111101|000          7ffffe8  [27]\n" +
     "       (246)  |11111111|11111111|11111101|001          7ffffe9  [27]\n" +
     "       (247)  |11111111|11111111|11111101|010          7ffffea  [27]\n" +
     "       (248)  |11111111|11111111|11111101|011          7ffffeb  [27]\n" +
     "       (249)  |11111111|11111111|11111111|1110         ffffffe  [28]\n" +
     "       (250)  |11111111|11111111|11111101|100          7ffffec  [27]\n" +
     "       (251)  |11111111|11111111|11111101|101          7ffffed  [27]\n" +
     "       (252)  |11111111|11111111|11111101|110          7ffffee  [27]\n" +
     "       (253)  |11111111|11111111|11111101|111          7ffffef  [27]\n" +
     "       (254)  |11111111|11111111|11111110|000          7fffff0  [27]\n" +
     "       (255)  |11111111|11111111|11111011|10           3ffffee  [26]\n" +
     "   EOS (256)  |11111111|11111111|11111111|111111      3fffffff  [30]";
    // @formatter:on

    @Test
    public void read_table() {
        Pattern line = Pattern.compile(
                "\\(\\s*(?<ascii>\\d+)\\s*\\)\\s*(?<binary>(\\|(0|1)+)+)\\s*" +
                        "(?<hex>[0-9a-zA-Z]+)\\s*\\[\\s*(?<len>\\d+)\\s*\\]");
        Matcher m = line.matcher(SPEC);
        int i = 0;
        while (m.find()) {
            String ascii = m.group("ascii");
            String binary = m.group("binary").replaceAll("\\|", "");
            String hex = m.group("hex");
            String len = m.group("len");

            // Several sanity checks for the data read from the table, just to
            // make sure what we read makes sense
            assertEquals(parseInt(len), binary.length());
            assertEquals(parseInt(binary, 2), parseInt(hex, 16));

            int expected = parseInt(ascii);

            // TODO: find actual eos, do not hardcode it!
            byte[] bytes = intToBytes(0x3fffffff, 30,
                    parseInt(hex, 16), parseInt(len));

            StringBuilder actual = new StringBuilder();
            Huffman.Reader t = new Huffman.Reader();
            t.read(ByteBuffer.wrap(bytes), actual, false, true);

            // What has been read MUST represent a single symbol
            assertEquals(actual.length(), 1, "ascii: " + ascii);

            // It's a lot more visual to compare char as codes rather than
            // characters (as some of them might not be visible)
            assertEquals(actual.charAt(0), expected);
            i++;
        }
        assertEquals(i, 257); // 256 + EOS
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.4.1
    //
    @Test
    public void read_1() {
        read("f1e3 c2e5 f23a 6ba0 ab90 f4ff", "www.example.com");
    }

    @Test
    public void write_1() {
        write("www.example.com", "f1e3 c2e5 f23a 6ba0 ab90 f4ff");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.4.2
    //
    @Test
    public void read_2() {
        read("a8eb 1064 9cbf", "no-cache");
    }

    @Test
    public void write_2() {
        write("no-cache", "a8eb 1064 9cbf");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.4.3
    //
    @Test
    public void read_3() {
        read("25a8 49e9 5ba9 7d7f", "custom-key");
    }

    @Test
    public void write_3() {
        write("custom-key", "25a8 49e9 5ba9 7d7f");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.4.3
    //
    @Test
    public void read_4() {
        read("25a8 49e9 5bb8 e8b4 bf", "custom-value");
    }

    @Test
    public void write_4() {
        write("custom-value", "25a8 49e9 5bb8 e8b4 bf");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.1
    //
    @Test
    public void read_5() {
        read("6402", "302");
    }

    @Test
    public void write_5() {
        write("302", "6402");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.1
    //
    @Test
    public void read_6() {
        read("aec3 771a 4b", "private");
    }

    @Test
    public void write_6() {
        write("private", "aec3 771a 4b");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.1
    //
    @Test
    public void read_7() {
        read("d07a be94 1054 d444 a820 0595 040b 8166 e082 a62d 1bff",
                "Mon, 21 Oct 2013 20:13:21 GMT");
    }

    @Test
    public void write_7() {
        write("Mon, 21 Oct 2013 20:13:21 GMT",
                "d07a be94 1054 d444 a820 0595 040b 8166 e082 a62d 1bff");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.1
    //
    @Test
    public void read_8() {
        read("9d29 ad17 1863 c78f 0b97 c8e9 ae82 ae43 d3",
                "https://www.example.com");
    }

    @Test
    public void write_8() {
        write("https://www.example.com",
                "9d29 ad17 1863 c78f 0b97 c8e9 ae82 ae43 d3");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.2
    //
    @Test
    public void read_9() {
        read("640e ff", "307");
    }

    @Test
    public void write_9() {
        write("307", "640e ff");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.3
    //
    @Test
    public void read_10() {
        read("d07a be94 1054 d444 a820 0595 040b 8166 e084 a62d 1bff",
                "Mon, 21 Oct 2013 20:13:22 GMT");
    }

    @Test
    public void write_10() {
        write("Mon, 21 Oct 2013 20:13:22 GMT",
                "d07a be94 1054 d444 a820 0595 040b 8166 e084 a62d 1bff");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.3
    //
    @Test
    public void read_11() {
        read("9bd9 ab", "gzip");
    }

    @Test
    public void write_11() {
        write("gzip", "9bd9 ab");
    }

    //
    // https://tools.ietf.org/html/rfc7541#appendix-C.6.3
    //
    @Test
    public void read_12() {
        read("94e7 821d d7f2 e6c7 b335 dfdf cd5b 3960 " +
             "d5af 2708 7f36 72c1 ab27 0fb5 291f 9587 " +
             "3160 65c0 03ed 4ee5 b106 3d50 07",
             "foo=ASDJKHQKBZXOQWEOPIUAXQWEOIU; max-age=3600; version=1");
    }

    @Test
    public void test_trie_has_no_empty_nodes() {
        Huffman.Node root = Huffman.INSTANCE.getRoot();
        Stack<Huffman.Node> backlog = new Stack<>();
        backlog.push(root);
        while (!backlog.isEmpty()) {
            Huffman.Node n = backlog.pop();
            // The only type of nodes we couldn't possibly catch during
            // construction is an empty node: no children and no char
            if (n.left != null) {
                backlog.push(n.left);
            }
            if (n.right != null) {
                backlog.push(n.right);
            }
            assertFalse(!n.charIsSet && n.left == null && n.right == null,
                    "Empty node in the trie");
        }
    }

    @Test
    public void test_trie_has_257_nodes() {
        int count = 0;
        Huffman.Node root = Huffman.INSTANCE.getRoot();
        Stack<Huffman.Node> backlog = new Stack<>();
        backlog.push(root);
        while (!backlog.isEmpty()) {
            Huffman.Node n = backlog.pop();
            if (n.left != null) {
                backlog.push(n.left);
            }
            if (n.right != null) {
                backlog.push(n.right);
            }
            if (n.isLeaf()) {
                count++;
            }
        }
        assertEquals(count, 257);
    }

    @Test
    public void cant_encode_outside_byte() {
        TestHelper.Block<Object> coding =
                () -> new Huffman.Writer()
                        .from(((char) 256) + "", 0, 1)
                        .write(ByteBuffer.allocate(1));
        RuntimeException e =
                TestHelper.assertVoidThrows(RuntimeException.class, coding);
        TestHelper.assertExceptionMessageContains(e, "char");
    }

    private static void read(String hexdump, String decoded) {
        ByteBuffer source = SpecHelper.toBytes(hexdump);
        Appendable actual = new StringBuilder();
        new Huffman.Reader().read(source, actual, true);
        assertEquals(actual.toString(), decoded);
    }

    private static void write(String decoded, String hexdump) {
        int n = Huffman.INSTANCE.lengthOf(decoded);
        ByteBuffer destination = ByteBuffer.allocate(n); // Extra margin (1) to test having more bytes in the destination than needed is ok
        Huffman.Writer writer = new Huffman.Writer();
        BuffersTestingKit.forEachSplit(destination, byteBuffers -> {
            writer.from(decoded, 0, decoded.length());
            boolean written = false;
            for (ByteBuffer b : byteBuffers) {
                int pos = b.position();
                written = writer.write(b);
                b.position(pos);
            }
            assertTrue(written);
            ByteBuffer concated = BuffersTestingKit.concat(byteBuffers);
            String actual = SpecHelper.toHexdump(concated);
            assertEquals(actual, hexdump);
            writer.reset();
        });
    }

    //
    // It's not very pretty, yes I know that
    //
    //      hex:
    //
    //      |31|30|...|N-1|...|01|00|
    //                  \        /
    //                  codeLength
    //
    //      hex <<= 32 - codeLength; (align to MSB):
    //
    //      |31|30|...|32-N|...|01|00|
    //        \        /
    //        codeLength
    //
    //      EOS:
    //
    //      |31|30|...|M-1|...|01|00|
    //                   \        /
    //                   eosLength
    //
    //      eos <<= 32 - eosLength; (align to MSB):
    //
    //      pad with MSBs of EOS:
    //
    //      |31|30|...|32-N|32-N-1|...|01|00|
    //                     |    32|...|
    //
    //      Finally, split into byte[]
    //
    private byte[] intToBytes(int eos, int eosLength, int hex, int codeLength) {
        hex <<= 32 - codeLength;
        eos >>= codeLength - (32 - eosLength);
        hex |= eos;
        int n = (int) Math.ceil(codeLength / 8.0);
        byte[] result = new byte[n];
        for (int i = 0; i < n; i++) {
            result[i] = (byte) (hex >> (32 - 8 * (i + 1)));
        }
        return result;
    }
}

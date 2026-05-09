/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.connectors.seatunnel.jdbc.source;

import java.math.BigInteger;

final class AsciiStringRangeSplitter {

    private static final int RADIX = 128;

    private AsciiStringRangeSplitter() {}

    // The numeric mapping is only order-preserving for fixed-length printable ASCII strings under
    // binary/byte-wise collation. Variable-length keys must fall back to hash or single splitting.
    static String[] split(String left, String right, int expectSliceNumber) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("String range boundary cannot be null");
        }
        validateRangeBoundary(left, "left");
        validateRangeBoundary(right, "right");
        if (left.length() != right.length()) {
            throw new IllegalArgumentException(
                    "String range split requires fixed-length ASCII boundaries");
        }
        if (left.equals(right)) {
            return new String[] {left, right};
        }
        if (left.compareTo(right) > 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "String range left boundary [%s] must not be greater than right boundary [%s]",
                            left, right));
        }
        BigInteger[] tempResult =
                splitBigInteger(
                        stringToBigInteger(left), stringToBigInteger(right), expectSliceNumber);
        String[] result = new String[tempResult.length];
        result[0] = left;
        result[tempResult.length - 1] = right;

        for (int i = 1; i < tempResult.length - 1; i++) {
            result[i] = bigIntegerToString(tempResult[i], left.length());
            validateRangeBoundary(result[i], "generated");
        }
        validateLexicalOrder(result);
        return result;
    }

    private static BigInteger[] splitBigInteger(
            BigInteger left, BigInteger right, int expectSliceNumber) {
        if (expectSliceNumber < 1) {
            throw new IllegalArgumentException("expectSliceNumber must be greater than 0");
        }
        if (left.compareTo(right) == 0) {
            return new BigInteger[] {left, right};
        }
        BigInteger gap = right.subtract(left);
        BigInteger step = gap.divide(BigInteger.valueOf(expectSliceNumber));
        BigInteger remainder = gap.remainder(BigInteger.valueOf(expectSliceNumber));
        if (step.compareTo(BigInteger.ZERO) == 0) {
            expectSliceNumber = Math.max(remainder.intValue(), 1);
        }

        BigInteger[] result = new BigInteger[expectSliceNumber + 1];
        result[0] = left;
        result[expectSliceNumber] = right;

        BigInteger upperBound = left;
        for (int i = 1; i < expectSliceNumber; i++) {
            upperBound = upperBound.add(step);
            upperBound =
                    upperBound.add(
                            remainder.compareTo(BigInteger.valueOf(i)) >= 0
                                    ? BigInteger.ONE
                                    : BigInteger.ZERO);
            result[i] = upperBound;
        }
        return result;
    }

    private static BigInteger stringToBigInteger(String value) {
        BigInteger result = BigInteger.ZERO;
        BigInteger radixBigInteger = BigInteger.valueOf(RADIX);
        int index = 0;

        for (int i = value.length() - 1; i >= 0; i--) {
            char ch = value.charAt(i);
            result = result.add(BigInteger.valueOf(ch).multiply(radixBigInteger.pow(index)));
            index++;
        }
        return result;
    }

    private static String bigIntegerToString(BigInteger value, int length) {
        char[] result = new char[length];
        BigInteger current = value;
        BigInteger radixBigInteger = BigInteger.valueOf(RADIX);

        for (int i = length - 1; i >= 0; i--) {
            int remainder = current.mod(radixBigInteger).intValue();
            result[i] = (char) remainder;
            current = current.divide(radixBigInteger);
        }
        if (current.compareTo(BigInteger.ZERO) > 0) {
            throw new IllegalArgumentException("Generated string boundary exceeds fixed length");
        }
        return new String(result);
    }

    private static void validateRangeBoundary(String value, String name) {
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || ch > 126) {
                throw new IllegalArgumentException(
                        String.format(
                                "Only printable ASCII strings are supported for string range split, %s=[%s]",
                                name, value));
            }
        }
    }

    private static void validateLexicalOrder(String[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i - 1].compareTo(values[i]) >= 0) {
                throw new IllegalArgumentException(
                        String.format(
                                "Generated string range boundary [%s] is not greater than previous boundary [%s]",
                                values[i], values[i - 1]));
            }
        }
    }
}

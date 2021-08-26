package com.github.ntsee.jeobot.io;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EOSerializer {

    public static final int ONE_BYTE_MAX = 253;
    public static final int TWO_BYTE_MAX = 64009;
    public static final int THREE_BYTE_MAX = 16194277;
    private static final List<Byte> BUFFER = new ArrayList<>();

    private int sequenceStart;
    private int sequenceValue;
    private int encode;
    private int decode;

    public EOSerializer() {
        this.sequenceStart = 0;
        this.sequenceValue = 0;
        this.encode = 0;
        this.decode = 0;
    }

    public void initialize(int s1, int s2, int encode, int decode) {
        this.sequenceStart = s1 * 7 + s2 - 13;
        this.encode = encode;
        this.decode = decode;
    }

    public void setNewSequenceStart(int s1, int s2) {
        this.sequenceStart = s1 - s2;
    }

    public List<Byte> encode(EOWriter packet) {
        List<Byte> data = packet.getData();
        int sequence = this.sequence();
        if (this.isInitialized()) {
            int length = (sequence >= ONE_BYTE_MAX) ? 2 : 1;
            byte[] bytes = numberToBytes(sequence, length);
            data.add(2, bytes[0]);
            if (length > 1) {
                data.add(3, bytes[1]);
            }


            swapMultiples(data, this.decode);
            interleave(data);
            flipMSB(data);
        }

        byte[] length = numberToBytes(data.size(), 2);
        data.add(0, length[0]);
        data.add(1, length[1]);
        return new ArrayList<>(data);
    }

    public EOReader decode(List<Byte> data) {
        if (!isSerialized(data)) {
            return EOReader.fromNetwork(data);
        }

        flipMSB(data);
        deinterleave(data);
        swapMultiples(data, this.encode);
        return EOReader.fromNetwork(data);
    }


    private int sequence() {
        int val = this.sequenceValue;
        this.sequenceValue = (this.sequenceValue + 1) % 10;
        return this.sequenceStart + val;
    }

    private boolean isInitialized() {
        return this.encode != 0 && this.decode != 0;
    }

    public static boolean isSerialized(List<Byte> data) {
        return data.get(0) != (byte)0xFF && data.get(1) != (byte)0xFF;
    }

    public static void flipMSB(List<Byte> array) {
        for (int i=0; i<array.size(); i++) {
            int value = Byte.toUnsignedInt((byte) (array.get(i) ^ 0x80));
            if (value == 128) {
                value = 0;
            } else if (value == 0) {
                value = 128;
            }

            array.set(i, (byte)value);
        }
    }

    public static void swapMultiples(List<Byte> array, int multiple) {
        if (multiple == 0) {
            return;
        }

        for (int i=0, n=0; i<=array.size(); i++) {
            if (i != array.size() && (array.get(i) & 0xFF) % multiple == 0) {
                n++;
            } else {
                if (n > 1) {
                    for (int j=0; j<n/2; j++) {
                        byte temp = array.get(i - n + j);
                        array.set(i - n + j, array.get(i - j - 1));
                        array.set(i - j - 1, temp);
                    }
                }
                n = 0;
            }
        }
    }

    public static void deinterleave(List<Byte> array) {
        for (int i=0; i<array.size(); i+=2) {
            BUFFER.add(array.get(i));
        }

        int end = array.size() - (array.size() % 2 == 0 ? 1 : 2);
        for (int i=end; i>=0; i-=2) {
            BUFFER.add(array.get(i));
        }

        array.clear();
        array.addAll(BUFFER);
        BUFFER.clear();
    }

    public static void interleave(List<Byte> array) {
        for (int i=0; i<array.size()/2; i++) {
            BUFFER.add(array.get(i));
            BUFFER.add(array.get(array.size() - i - 1));
        }

        if (array.size() % 2 != 0) {
            BUFFER.add(array.get((array.size()/2)));
        }

        array.clear();
        array.addAll(BUFFER);
        BUFFER.clear();
    }

    public static int bytesToNumber(int a) {
        return bytesToNumber(a, 254, 254, 254);
    }

    public static int bytesToNumber(int a, int b) {
        return bytesToNumber(a, b, 254, 254);
    }

    public static int bytesToNumber(int a, int b, int c) {
        return bytesToNumber(a, b, c, 254);
    }

    public static int bytesToNumber(int a, int b, int c, int d) {
        a = (a == 0 || a == 254) ? 1 : a;
        b = (b == 0 || b == 254) ? 1 : b;
        c = (c == 0 || c == 254) ? 1 : c;
        d = (d == 0 || d == 254) ? 1 : d;
        return (--d * THREE_BYTE_MAX) + (--c * TWO_BYTE_MAX) + (--b * ONE_BYTE_MAX) + (--a);
    }

    public static byte[] numberToBytes(int number, int length) {
        int onumber = number;
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte)0xFE);

        if (length >= 4 && onumber >= THREE_BYTE_MAX) {
            bytes[3] = (byte)(Integer.divideUnsigned(number, THREE_BYTE_MAX) + 1);
            number %= THREE_BYTE_MAX;
        }

        if (length >= 3 && onumber >= TWO_BYTE_MAX) {
            bytes[2] = (byte)(Integer.divideUnsigned(number, TWO_BYTE_MAX) + 1);
            number %= TWO_BYTE_MAX;
        }

        if (length >= 2 && onumber >= ONE_BYTE_MAX) {
            bytes[1] = (byte)(Integer.divideUnsigned(number, ONE_BYTE_MAX) + 1);
            number %= ONE_BYTE_MAX;
        }

        if (length >= 1) {
            bytes[0] = (byte)(number + 1);
        }

        return bytes;
    }

    public static String decodeString(String string) {
        StringBuilder builder = new StringBuilder(string);
        builder.reverse();

        boolean flip = string.length() % 2 == 1;
        for (int i=0; i<string.length(); i++) {
            int c = builder.charAt(i);
            if (c == 0xFF) {
                builder.delete(i, string.length());
                break;
            }

            if (flip) {
                if (c >= 0x22 && c <= 0x4F) {
                    c = (0x71 - c) & 0xFF;
                } else if (c >= 0x50 && c <= 0x7E) {
                    c = (0xCD - c) & 0xFF;
                }
            } else if (c >= 0x22 && c <= 0x7E) {
                c = (0x9F - c) & 0xFF;
            }

            builder.setCharAt(i, (char)c);
            flip = !flip;
        }

        return builder.toString();
    }

    public static int[] unsignBytes(byte[] bytes) {
        int[] data = new int[bytes.length];
        for (int i=0; i<bytes.length; i++) {
            data[i] = bytes[i] & 0xFF;
        }

        return data;
    }
}

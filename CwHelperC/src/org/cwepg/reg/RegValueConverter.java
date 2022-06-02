/*
 * Created on Aug 10, 2009
 *
 */
package org.cwepg.reg;

import java.util.Map;

public class RegValueConverter {

    public static int getIntFromBinary(byte[] buf, int offset, int size) throws Exception {
        if (buf == null)  throw new Exception("getIntFromBinary(): Data was null.");
        int result = -1;
        if (size == 4){
            result = (((int)(buf[offset] & 0xff)) + (((int)(buf[offset + 1] & 0xff)) << 8) + (((int)(buf[offset + 2] & 0xff)) << 16) + (((int)(buf[offset + 3] & 0xff)) << 24));
        } else if (size == 2){
            result = (((int)(buf[offset] & 0xff)) + (((int)(buf[offset + 1] & 0xff)) << 8));
        } else if (size == 1){
            result = (int)(buf[offset] & 0xff);
        } else throw new Exception ("size" + size + " not supported.");
        return result;
    }

    public static String getStringFromBinary(byte[] buf, int stringOffset) throws Exception {
        byte blank = 0;
        int length = 0;
        for (int i = stringOffset; i < buf.length; i++, length++){
            if (buf[i] == blank) break;
        }
        return new String(buf, stringOffset, length, "UTF-8");
    }

    public static byte[] setStringIntoBinary(String valueToSet, byte[] buf, int stringOffset, int maxStringSize) {
        if (valueToSet == null || valueToSet.isEmpty()) return buf;
        byte[] vBytes = valueToSet.getBytes();
        for (int i = stringOffset, j = 0; i < (stringOffset+maxStringSize) && j < vBytes.length; i++, j++) {
            buf[i] = vBytes[j];
            buf[i+1] = 0;
        }
        return buf;
    }

    public static byte[] setIntIntoBinary(int valueToSet, byte[] buf, int intOffset) {
        buf[intOffset*4    ] = (byte) valueToSet;
        buf[intOffset*4 + 1] = (byte)(valueToSet >>> 8);
        buf[intOffset*4 + 2] = (byte)(valueToSet >>> 16);
        buf[intOffset*4 + 3] = (byte)(valueToSet >>> 24);
        return buf;
    }

    /**
     * Test Harness
     */
    public static void main(String[] args) throws Exception {

        String topKey = "HKEY_LOCAL_MACHINE";
        String myhdBranch = "SOFTWARE\\MyHD";


        boolean testIntFromBinary = false;
        /*********************************/
        if (testIntFromBinary){
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = (byte[])map.get("RESERVATION_INFO_VCH_016");
            int size = 4;
            for (int i = 0; i < 16; i++){
                int offset = i * 4;
                int aRegIntValue = RegValueConverter.getIntFromBinary(buf, offset, size);
                System.out.println(i + " " + aRegIntValue);
            }
        }
        boolean testIntFromDword = true;
        /*********************************/
        if (testIntFromDword){
            Map map = Registry.getValues(topKey, myhdBranch);
            int aRegIntValue = ((Integer)map.get("INPUT1_CH Tail_VCH")).intValue();
            int sameIntValue = Registry.getIntValue(topKey, myhdBranch, "INPUT1_CH Tail_VCH");
            System.out.println(aRegIntValue + "=" + sameIntValue);
        }

        
        boolean testIntSizesFromBinary = false;
        /*********************************/
        if (testIntSizesFromBinary){
            int count = Registry.getIntValue("HKEY_LOCAL_MACHINE","SOFTWARE\\MyHD", "INPUT1_CH Tail_VCH");
            System.out.println("There were " + count + " channels on input 1");
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = (byte[])map.get("INPUT1_CH_DATA_VCH_QAM");
            for(int i = 0; i < count; i++){
                int j = i * 40;
                System.out.println("tunerInput " + RegValueConverter.getIntFromBinary(buf,j,4));
                System.out.println("signal " + RegValueConverter.getIntFromBinary(buf,j+4,4));
                System.out.println("rf " + RegValueConverter.getIntFromBinary(buf,j+8,1));
                System.out.println("virm " + RegValueConverter.getIntFromBinary(buf,j+10,2));
                System.out.println("virs " + RegValueConverter.getIntFromBinary(buf,j+12,1));
                System.out.println("rfsub " + RegValueConverter.getIntFromBinary(buf,j+13,1));
                System.out.println("prog " + RegValueConverter.getIntFromBinary(buf,j+16,4));
                System.out.println("=============");
            }
        }

        boolean testStringFromBinary = false;
        /*********************************/
        if (testStringFromBinary){
            int stringOffset = 64; 
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = (byte[])map.get("RESERVATION_INFO_VCH_016");
            String aRegStringValue = RegValueConverter.getStringFromBinary(buf, stringOffset);
            System.out.println("String value at " + stringOffset + " was " + aRegStringValue);
        }
        
        boolean testSettingStringInBinary = false;
        /*********************************/
        if (testSettingStringInBinary){
            int stringOffset = 64;
            String valueToSet = "c:\\test\\testdata.tp";
            int maxStringSize = 259;
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = (byte[])map.get("RESERVATION_INFO_VCH_016");
            byte[] updatedBuf = RegValueConverter.setStringIntoBinary(valueToSet, buf, stringOffset, maxStringSize);
            System.out.println(valueToSet + "=" + RegValueConverter.getStringFromBinary(updatedBuf, stringOffset));
        }
        
        boolean testSettingIntInBinary = true;
        /*********************************/
        if (testSettingIntInBinary){
            int intOffset = 8;
            int valueToSet = 42;
            int size = 4;
            Map map = Registry.getValues(topKey, myhdBranch);
            byte[] buf = (byte[])map.get("RESERVATION_INFO_VCH_016");
            byte[] updatedBuf = RegValueConverter.setIntIntoBinary(valueToSet, buf, intOffset);
            System.out.println(valueToSet + "=" + RegValueConverter.getIntFromBinary(updatedBuf, intOffset, size));
        }
    }
}

package purse;

import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;
import javacard.security.DESKey;
import javacard.security.Key;
import javacard.security.KeyBuilder;
import javacardx.crypto.Cipher;

public class PenCipher {
	private Cipher desEngine;
	private Key deskey;
	
	public PenCipher(){
		/*
		 *  ��ü���ʵ��
		 *  ʹ��Cipher���е�getInstance()��������ü���ʵ��
		 */
		desEngine = Cipher.getInstance(Cipher.ALG_DES_CBC_NOPAD, false);
		/*
		 * ����DES��Կʵ��
		 * ʹ��KeyBuilder�е�buildKey()��������DES��Կʵ��
		 */
		deskey = KeyBuilder.buildKey(KeyBuilder.TYPE_DES, KeyBuilder.LENGTH_DES, false);
	}
	
	/*
	 * ���ܣ�DES����
	 * ������key ��Կ; kOff ��Կ��ƫ����; data ��Ҫ���мӽ��ܵ�����; dOff ����ƫ������ dLen ���ݵĳ���; r �ӽ��ܺ�����ݻ������� rOff �������ƫ������ mode ���ܻ��������ģʽ
	 * ���أ���
	 */
	public final void cdes(byte[] akey, short kOff, byte[] data, short dOff, short dLen, byte[] r, short rOff, byte mode){
		/*
		 * ����DES��Կ
		 * ʹ��DESKey�ӿ��е�setKey()����������Կֵ������akey����Կ��kOff����Կ��ƫ������
		 */
		((DESKey)deskey).setKey(akey, kOff);
		/*
		 * ��ʼ����Կ������ģʽ
		 * ʹ��Cipher���е�init()���������ü��ܶ���ʵ��
		 * ����mode�Ǽӽ���ģʽ��Cipher.MODE_ENCRYPT��Cipher.MODE_DECRYPT��
		 */
		desEngine.init(deskey, mode);
		/*
		 * ����
		 * ʹ��Cipher���е�doFinal()�������������
		 * ����data��dOff�ֱ��Ǽ������ݺ�ƫ������dLen�����ݳ��ȣ�r��rOff�ֱ��Ǽ��ܺ�����ݺ�ƫ������
		 */

		desEngine.doFinal(data, dOff, dLen, r, rOff);
	}
	
	/*
	 * ���ܣ����ɹ�����Կ
	 * ������key ��Կ�� data ��Ҫ���ܵ����ݣ� dOff �����ܵ�����ƫ������ dLen �����ܵ����ݳ��ȣ� r ���ܺ�����ݣ� rOff ���ܺ�����ݴ洢ƫ����
	 * ���أ���
	 * 3DES�㷨ʹ��3��DES�ӽ����㷨�����ݽ��м��㡣
	 * �����������ȼ��ܣ�������벿�֣����ٽ��ܣ������ְ벿�֣����ټ��ܣ�������벿�֣������и��ߵİ�ȫ�ԡ�
	 */
	public final void gen_SESPK(byte[] key, byte[]data, short dOff, short dLen, byte[] r, short rOff){
		//todo
		//cdes������key ��Կ; kOff ��Կ��ƫ����; data ��Ҫ���мӽ��ܵ�����; dOff ����ƫ������ dLen ���ݵĳ���; r �ӽ��ܺ�����ݻ������� rOff �������ƫ������ mode ���ܻ��������ģʽ
		
		//��dataʹ������Կ����벿�ּ��ܣ�д��data
		cdes(key,(short)0,data,(short)0,dLen,data,(short)0,Cipher.MODE_ENCRYPT);  
        
        //��dataʹ������Կ���Ұ벿�ֽ��ܣ�����kOffΪ0x08��д��data�����ܣ�����ģʽΪMODE_DECRYPT
        cdes(key,(short)8,data,(short)0,dLen,data,(short)0,Cipher.MODE_DECRYPT);  
        
        //��dataʹ������Կ����벿���ٴμ��ܣ�д��r����
        cdes(key,(short)0,data,(short)0,dLen,r,rOff,Cipher.MODE_ENCRYPT);  
	}
	
	/*
	 * ���ܣ�8���ֽڵ�������
	 * ������d1 ����������������1 d2:����������������2 d2_off:����2��ƫ����
	 * ���أ���
	 */
	public final void xorblock8(byte[] d1, byte[] d2, short d2_off){
		/*
		 * todo: �������ݿ�������������������ݿ�d1��
		 */
        for(byte i = 0; i<8; ++i) {  
            d1[i] ^= d2[i+d2_off];
        }
	}
	
	/*
	 * ���ܣ��ֽ����
	 * ������data ��Ҫ�������ݣ� len ���ݵĳ���
	 * ���أ�������ֽڳ���
	 */
	public final short pbocpadding(byte[] data, short len){
		//todo: ����ַ�����8�ı���
		
		//������䣺�����������β������0x80��������ݵ��ֽ����Ƿ�Ϊ8�ı�����
		//������㣬����β������0x00��ֱ������8�ı���Ϊֹ��
		//����ʧ��
		/*
		byte[] data_temp = new byte[(len>>3+1)*8];
		for(short i = 0; i<len; ++i){
			data_temp[i] = data[i];
		}
		data = new byte[(len>>3+1)*8];
		for(short i = 0; i<len; ++i){
			data[i] = data_temp[i];
		}
		*/
		 data[len++] = (byte)0x80;
		 for(;len%8 != 0;)
			 data[len++] = (byte)0x00;  
		 
		return len;
	}
	
	/*
	 * ���ܣ�MAC��TAC������
	 * ������key ��Կ; data ��Ҫ���ܵ�����; dl ��Ҫ���ܵ����ݳ��ȣ� mac ������õ���MAC��TAC��
	 * ���أ���
	 */
	public final void gmac4(byte[] key, byte[] data, short dl, byte[] mac){ 
        //todo  
        //����䣬�ٽ��ж���des
        short new_dl = pbocpadding(data,dl);
        
        //��ʼ����������mac_tac��
        //��ʼ������ֵ��Ψһ
        byte[] mac_tac = {0x11,0x11,0x11,0x11,0x11,0x11,0x11,0x11};  
        
		//����Щ���ݷָ�Ϊ8�ֽڳ������ݿ��顣
        short num = (short)(new_dl>>3); //�зֳɶ��ٿ� 
        
        /* 
         * ��03 ����Ǯ���Ĺ�����P13 mac_tac������
         * cdes������key ��Կ; kOff ��Կ��ƫ����; data ��Ҫ���мӽ��ܵ�����; dOff ����ƫ������ dLen ���ݵĳ���; r �ӽ��ܺ�����ݻ������� rOff �������ƫ������ mode ���ܻ��������ģʽ
         */
        for(short i = 0; i < num; ++i) {
            xorblock8(mac_tac, data, (short)(i<<3));
            cdes(key,(short)0,mac_tac,(short)0,(short)8,mac_tac,(short)0,Cipher.MODE_ENCRYPT);  
        }  
        //macֻ��4���ֽڣ�����ֻ��ȡǰ��λ���бȽ�
        for(byte i = 0; i < 4; i++) {
            mac[i] = mac_tac[i];  
        }  
	}
	
}
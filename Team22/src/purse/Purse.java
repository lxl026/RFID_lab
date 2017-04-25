/**
 * 
 */
package purse;

import javacard.framework.APDU;
import javacard.framework.ISO7816;
import javacard.framework.Applet;
import javacard.framework.ISOException;

/**
 * @author yunyao
 *
 */
import javacard.framework.JCSystem;
import javacard.framework.Util;

public class Purse extends Applet {
	//APDU Object
	private Papdu papdu;
	
	//�ļ�ϵͳ
	private KeyFile keyfile;            //��Կ�ļ�
	private BinaryFile cardfile;       	//Ӧ�û����ļ�
	private BinaryFile personfile;     	//�ֿ��˻����ļ�
	private EPFile EPfile;              //����Ǯ���ļ�
	
	public Purse(byte[] bArray, short bOffset, byte bLength){
		papdu = new Papdu();
		// ��bArray����ռ�
		byte aidLen = bArray[bOffset];
		if(aidLen == (byte)0x00)
			register();
		else
			register(bArray, (short)(bOffset + 1), aidLen);
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// �����ܿ���д��ĳ���ļ�
		new Purse(bArray, bOffset, bLength);
	}

	public void process(APDU apdu) {
		// ���AppletΪ�գ��˳�
		if (selectingApplet()) {
			return;
		}
		
		//����1:ȡAPDU�������������ò���֮�����½�����
		byte apdu_buffer[] = apdu.getBuffer();
		//����2��ȡAPDU�����������ݷŵ�����papdu
		//��apdu��ȡ����Ƭ���������в�����data�εĳ���  
		short lc = apdu.setIncomingAndReceive();
        papdu.cla = apdu_buffer[ISO7816.OFFSET_CLA];
        papdu.ins = apdu_buffer[ISO7816.OFFSET_INS];
        papdu.p1 = apdu_buffer[ISO7816.OFFSET_P1];
        papdu.p2 = apdu_buffer[ISO7816.OFFSET_P2];
        Util.arrayCopyNonAtomic(apdu_buffer, ISO7816.OFFSET_CDATA, papdu.pdata, (short) 0, lc);
	        
		/*
		 * ����3���ж�����APDU�Ƿ�������ݶΣ����������ȡ���ݳ��ȣ�����le��ֵ
		 * ���򣬼�����Ҫlc��data�����ȡ������ԭ��lcʵ������le
		 * ��ȡle�ķ�������Ϊ��ȷ��papdu��le���֣�����IOS7816�±��ѡ�û��le���Ƿ������ݿ��е�. 
		 * ��������ݿ飬��le����buffer[ISO7816.OFFSET_CDATA+lc]  
		 * ����papdu�����ж�,����ֱ��ͨ��lc�ж�,��Ϊûlcֻ��leҲ���le����lc  
		 * 
		 * ��papdu����������ݿ�  ������Ҫ����le
		 * ���򣬽�data�ĳ���ֱ�Ӹ�ֵ��le
		 * ע��LE�ֽڱ�ʾ����������Ƭ���������ֽڳ���
		 */
		
		if(papdu.APDUContainData()) {
		    papdu.le = apdu_buffer[ISO7816.OFFSET_CDATA+lc];  
		    papdu.lc = apdu_buffer[ISO7816.OFFSET_LC];
		}  else {  
		    papdu.le = apdu_buffer[ISO7816.OFFSET_LC];
		    papdu.lc = 0;  
		}  
		// rc��ȡ�������ݣ��жϲ����Ƿ�ɹ�
        boolean rc = handleEvent();
		//����4:�ж��Ƿ���Ҫ�������ݣ�������apdu������	
        //if(papdu.le != 0)
        // ����ɹ����򷵻����ݣ���������apdu������
        if( rc ) {
            Util.arrayCopyNonAtomic(papdu.pdata, (short)0, apdu_buffer, (short)5, (short)papdu.pdata.length);  
            apdu.setOutgoingAndSend((short)5, papdu.le);//�ѻ����������ݷ��ظ��ն�  
        }
	}

	/*
	 * ���ܣ�������ķ����ʹ���
	 * ��������
	 * ���أ��Ƿ�ɹ�����������
	 * ��01 Java���ܿ�֮������ P30
	 */
	private boolean handleEvent(){
		switch(papdu.ins){
			case condef.INS_CREATE_FILE:return create_file(); 				// 0xE0 �ļ�����
			//todo�����д��������������������д��Կ����
            case condef.INS_WRITE_KEY: 	return write_key();  				// 0xD4 д��Կ
            case condef.INS_WRITE_BIN:	return write_binary();		  		// 0xD6 д�������ļ�
            case condef.INS_READ_BIN:	return read_binary();  				// 0xB0���������ļ�
    		case condef.INS_NIIT_TRANS:	
    			if (papdu.p1 == (byte) 0x00) return init_load();
                if (papdu.p1 == (byte) 0x01) return init_purchase();
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);				// 0x50���ѳ�ʼ������Ȧ���ʼ��
    		case condef.INS_LOAD:		return load();						// 0x52Ȧ��
    		case condef.INS_PURCHASE:	return purchase();					// 0x54����
    		case condef.INS_GET_BALANCE:return get_balance();				// 0x5c��ѯ���
		}	
		ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); // 0x6D00 ��ʾ CLA���� 
		return false;
	}
	
	/*
	 * д��Կ  
	 * ��Կ�ļ��ǰ��ռ�¼��һд��ģ�Ҳ�����м�����Կ��¼������Ҫ���ͼ�����д��Կ�������
	 * �ն˷��͡�д��Կ�������Ƭִ�ж�Ӧ�Ĳ�����ȡ�������е����ݣ���д���ѽ�������Կ�ļ��С�
	 */
    private boolean write_key() {
    	
    	//ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
        
        if( papdu.cla != (byte)0x80)
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED); 
        
        /*if(papdu.ins != condef.INS_WRITE_KEY)
            ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);   */
  
        //��Կ�ļ���p1Ϊ0x00�� p2
        //write_key��p1����Ϊ0x00��p2�ز���0x16��0x18������������������ļ�
        /*if(papdu.p1 != (byte)0x00 || (papdu.p2 >= (byte)0x16 && papdu.p2 <= (byte)0x18))  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/
        if (papdu.p2 != (byte) 0x06 && papdu.p2 != (byte) 0x07 && papdu.p2 != (byte) 0x08)
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
        
        //��Կ�����Ƿ���ȷ
        if(papdu.lc == 0 || papdu.lc > (byte)0x15)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        //�ļ��ռ�����  
        if(keyfile.recNum >= 3)
            ISOException.throwIt(ISO7816.SW_FILE_FULL);  
  
        this.keyfile.addkey(papdu.p2, papdu.lc, papdu.pdata);//д��һ����Կ  
  
        return true;  
    }  
    
    /**
     * д�������ļ�
     * д�������ļ�ֻ��Ҫһ������
     * ������������ݻ����Ҫ����
     * ��д���Ѵ����ĳֿ��˻����ļ���Ӧ�û����ļ��У�
     * ������ע�⣺д��ǰҪ�ȼ��д��������Ƿ񳬹��ļ��޶��Ĵ�С
     * @return
     */
    private boolean write_binary() {  
    	
    	//�����ж�cla�Ƿ�ȷʵΪ0x00�����ܼ���ִ��
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
    	
        //ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
  
        //д��ǰҪ�ȼ��д��������Ƿ񳬹��ļ��޶��Ĵ�С
        //д��data����Ӧ����1-255֮��
        if(papdu.lc == 0x00)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        /*
         * �ֿ��˻��߿���Ӧ��Ϊ�գ������Ϊ�վͿ��Կ�ʼд�ļ���
         */
        //Ӧ�û����ļ�Ϊ0x16��Ӧ���ļ���Ӧ��Ϊ��
        if(papdu.p1 == (byte)0x16 && cardfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        //�ֿ��˻����ļ�Ϊ0x17���ֿ��˲�Ӧ��Ϊnull
        if(papdu.p1 == (byte)0x17 && personfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  

        //Ӧ�û����ļ�Ϊ0x16�� �ֿ��˻����ļ�Ϊ0x17
        if(papdu.p1 == (byte)0x16){
        	this.cardfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata); 
        } else if(papdu.p1 == (byte)0x17){
        	this.personfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);  
        }
  
        return true;  
    }  
    
    /**
     * ��ȡ�������ļ�
     * ����  
     * @return
     */
    private boolean read_binary() {  
    	
    	//�����ж�cla�Ƿ�ȷʵΪ0x00�����ܼ���ִ��
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
    	
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);

        //Ӧ�û����ļ�Ϊ0x16��Ӧ���ļ���Ӧ��Ϊ��
        if(papdu.p1 == (byte)0x16 && cardfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        //�ֿ��˻����ļ�Ϊ0x17���ֿ��˲�Ӧ��Ϊnull
        if(papdu.p1 == (byte)0x17 && personfile == null)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        //��ȡ��Ӧ�Ķ����ļ�  
        if(papdu.p1 == (byte)0x16){
            this.cardfile.read_binary(papdu.p2, papdu.le, papdu.pdata);
        }
        else if(papdu.p1 == (byte)0x17){
            this.personfile.read_binary(papdu.p2, papdu.le, papdu.pdata);
        }
  
        return true;  
    }  
	
	/*
	 * ���ܣ������ļ�
	 */
	private boolean create_file() {
		// �ж�DATA���ļ�������Ϣ��AEF��
		switch(papdu.pdata[0]){
		case condef.EP_FILE:        return EP_file();   	// 0x2F����Ǯ���ļ�
		//todo:��ɴ�����Կ�ļ����ֿ��˻����ļ���Ӧ�û����ļ�
        case condef.PERSON_FILE:	return Person_file(); 	// 0x39�ֿ��˻����ļ�
        case condef.CARD_FILE:	    return Card_file();  	// 0x38Ӧ�û����ļ�
		case condef.KEY_FILE:      	return Key_file();  	// 0x3F��Կ�ļ�
		default: 
			ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
		}
		return true;
	}
	/*
	 * ���ܣ���������Ǯ���ļ�
	 * �����������ļ���ʵ�ַ�ʽ����
	 */
	private boolean EP_file() {
		// CLA��ʶ����ӿ�
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		/*//EP��ins����Ϊ0xE0��У�����������
		if(papdu.ins != condef.INS_CREATE_FILE)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

		//p1Ӧ��Ϊ0x00��p2Ӧ��Ϊ0x18
		if(papdu.p1 != (byte)0x00 || papdu.p2 != (byte)0x18)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/
		
		// LC��Data Field֮����
		// �ļ�����ʱ�ļ���Ϣ����Ϊ0x07
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		// ����Ѿ�������
		if(EPfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        
		//ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
		
        this.EPfile = new EPFile(keyfile);
		
		return true;
	}	

	/*
	 * 
	 * �ֿ�����Ϣ�ļ�  
	 */
    private boolean Person_file() {  
    	
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  

		/*//EP��ins����Ϊ0xE0��У�����������
		if(papdu.ins != condef.INS_CREATE_FILE)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		
		//p1Ӧ��Ϊ0x00, p2Ӧ��Ϊ0x17
		if(papdu.p1 != (byte)0x00 || papdu.p2 != (byte)0x17)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/
		
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(personfile != null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
        
        //ÿ��Ӧ��ֻ����һ��KEY�ļ����ұ������Ƚ���
        //��02 ����Ǯ�����ļ�ϵͳ��P19
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //������д��personfile
        this.personfile = new BinaryFile(papdu.pdata);
  
        return true;  
    }  
	    
    /*
     * Key_file()
     * ��Կ�ļ�  
     * ����EP_fileʵ��
     */
    private boolean Key_file() {

        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  

		//EP��ins����Ϊ0xE0��У�����������
		/*if(papdu.ins != condef.INS_CREATE_FILE)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

		//p1Ӧ��Ϊ0x00�� p2Ӧ��Ϊ0x00
		if(papdu.p1 != (byte)0x00 || papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/
		
        // LC Ӧ��Ϊ0x07, word��˵��15�д�
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(keyfile != null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //keyfile��Key_file�д������ʲ���Ҫ�ж��Ƿ�Ϊnull
        this.keyfile = new KeyFile();
  
        return true;  
    }  
    
    /*
     * Ӧ�� 
     * ����Ӧ�û����ļ�  
     */
    private boolean Card_file() {  
    	
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  

		//EP��ins����Ϊ0xE0��У�����������
		/*if(papdu.ins != condef.INS_CREATE_FILE)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);

		//p1Ӧ��Ϊ0x00, p2Ӧ��Ϊ0x16
		if(papdu.p1 != (byte)0x00 || papdu.p2 != 0x16)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/
		
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(cardfile != null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        
        if(keyfile == null)
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        //������д��cardfile
        this.cardfile = new BinaryFile(papdu.pdata);
  
        return true;  
    }  
    
	/*
	 * ���ܣ�Ȧ���ʼ�������ʵ��
	 */
	private boolean init_load() {
		short num,rc = 0;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		/*if(papdu.ins != (byte)0x50)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);*/
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x02)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		//pdata[0]���������ţ�����������Ѱ����Կ
		num = keyfile.findkey(papdu.pdata[0]);
		
		if(num == 0x00)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		/*
		 * ���EPFile��ֻ�᷵��2����0�� 2��ʾ���0��ʾ�ɹ�
		 * �����������Կ��ʶ��num��[���׽�� | �ն˻����]papdu.pdata
		 * ������ڲ���2�У��ṹ����
		 * ���4bytes | �����������к�2bytes | ��Կ�汾��1byte | �㷨��ʶ1byte | α�����4bytes | mac14bytes
		 * eg�� 08 00 00 10 00 00 11 22 33 44 55 10
		 * 00 01 00 46 C0 E4 3D 99 5F 3E 5F
		 */
		rc = EPfile.init4load(num, papdu.pdata);
		
		if(rc == 2)
			ISOException.throwIt((condef.SW_LOAD_FULL));		
		
		papdu.le = (short)0x10;
		
		return true;
	}
    
    /*
	 * ���ܣ�Ȧ�������ʵ��
	 */
	private boolean load() {
		short rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

		/*if(papdu.ins != (byte)0x52)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);*/
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		rc = EPfile.load(papdu.pdata);
		
		if(rc == 1)//MAC2��֤δͨ��
			ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
		else if(rc == 2)//
			ISOException.throwIt(condef.SW_LOAD_FULL);
		else if(rc == 3)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		papdu.le = (short)0x04;
		
		return true;
	}
	
	/*
	 * ���ܣ����ѳ�ʼ����ʵ��
	 */
	private boolean init_purchase(){
		short num,rc = 0;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

		/*if(papdu.ins != (byte)0x50)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);*/
		
		//p1Ӧ��Ϊ0x01��p2Ӧ��Ϊ0x02
		if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x02)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		//����tagѰ����Կ������Կ�ļ�¼��  
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);

		// ���EPfile���ڣ��Ͳ������ж�Keyfile�Ƿ�����ˣ���ΪKeyfile������EPfile��ǰ��
		num = keyfile.findkey(papdu.pdata[0]);
		
		/* �Ҳ�����Ӧ����Կ
		 * IC��������Կ��ʶ��������Կ�ļ��в��Ҹ���Կ��ʶ����Ӧ��������Կ
		 * ����Ҳ������ͷ���״̬�֡�9403������ʾ���������Ӧ����Կ��
		 * ����ҵ��Ļ����ͽ������µĴ���
		 */
		if(num == 0x00)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		//����Ĭ�ϵ�init4purchase������������
		rc = EPfile.init4purchase(num, papdu.pdata);
		
		if(rc == 2)
			ISOException.throwIt((condef.SW_BALANCE_NOT_ENOUGH));
		
		//init_purchase��ʼ���ڴ���LEΪ0x0F��������0x10
		papdu.le = (short)0x0F;
		
		return true;
	}
	
	/*
	 * ���ܣ����������ʵ��
	 */
	private boolean purchase(){
		short rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);

		/*if(papdu.ins != (byte)0x54)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);*/
		
		if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);

		//ISOException.throwIt((short) papdu.lc);
		if(papdu.lc != (short)0x0F)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		rc = EPfile.purchase(papdu.pdata);
		
		if(rc == 1)//MAC2��֤δͨ��
			ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
		else if(rc == 2)//������С�ڽ��׽��ͷ���״̬��9401
			ISOException.throwIt(condef.SW_BALANCE_NOT_ENOUGH);
		else if(rc == 3)//����ʧ��
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		papdu.le = (short)0x08;
		return true;
	}
	
	/*
	 * ���ܣ�����ѯ���ܵ�ʵ��
	 */
	private boolean get_balance(){
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
		/*if(papdu.ins != (byte)0x5C)
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);*/
        
        if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x02)  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);  
  
        short result;  
        byte[] balance = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);//����ݴ�  
        result = EPfile.get_balance(balance);  
  
        if(result == (short)0)  
            Util.arrayCopyNonAtomic(balance, (short)0, papdu.pdata, (short)0, (short)4);//���data[0]~data[3]  
  
        papdu.le = (short)0x04;  
  
        return true;  
	}
}
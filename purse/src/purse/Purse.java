package purse;

import javacard.framework.APDU;
import javacard.framework.Applet;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.JCSystem;
import javacard.framework.Util;

/**
 * 参考文献：http://blog.csdn.net/supergame111/article/details/5701106
 * 很全面
 * @author yunyao
 *
 */

public class Purse extends Applet {
	//APDU Object
	private Papdu papdu;
	
	//文件系统
	private KeyFile keyfile;            //密钥文件
	private BinaryFile cardfile;       //应用基本文件
	private BinaryFile personfile;     //持卡人基本文件
	private EPFile EPfile;              //电子钱包文件
	
	public Purse(byte[] bArray, short bOffset, byte bLength){
		papdu = new Papdu();
		// 给bArray分配空间
		byte aidLen = bArray[bOffset];
		if(aidLen == (byte)0x00)
			register();
		else
			register(bArray, (short)(bOffset + 1), aidLen);
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// 向智能卡中写入某种文件
		new Purse(bArray, bOffset, bLength);
	}

	public void process(APDU apdu) {
		// 如果Applet为空，退出
		if (selectingApplet()) {
			return;
		}		
		//步骤1:取APDU缓冲区数组引用并将之赋给新建数组
		byte apdu_buffer[] = apdu.getBuffer();  // return null???
		//步骤2：取APDU缓冲区中数据放到变量papdu
		//将apdu读取到卡片缓冲区当中并返回data段的长度  
		short lc = apdu.setIncomingAndReceive();
		papdu.cla = apdu_buffer[0];  
        papdu.ins = apdu_buffer[1];  
        papdu.p1 = apdu_buffer[2];  
        papdu.p2 = apdu_buffer[3];  
        Util.arrayCopyNonAtomic(apdu_buffer, (short)5, papdu.pdata, (short)0, lc); 
		//步骤3：判断命令APDU是否包含数据段，有数据则获取数据长度，并对le赋值
        //否则，即不需要lc和data，则获取缓冲区原本lc实际上是le
		//获取le的方法，因为不确定papdu有le部分，所以IOS7816下标可选项并没有le而是放在数据块中的.  
		//如果有数据块，那le就是buffer[ISO7816.OFFSET_CDATA+lc]  
		//调用papdu函数判断,不能直接通过lc判断,因为没lc只有le也会把le赋给lc  
		if(papdu.APDUContainData()) {//若papdu命令包含数据块  
		    papdu.le = apdu_buffer[ISO7816.OFFSET_CDATA+lc];  
		    papdu.lc = apdu_buffer[ISO7816.OFFSET_LC];  
		}  
		else  
		{  
		    papdu.le = apdu_buffer[ISO7816.OFFSET_LC];//若没data部分则lc部分实际是le  
		    papdu.lc = 0;  
		}  
		// rc获取返回数据，判断操作是否成功
        boolean rc = handleEvent();
		//步骤4:判断是否需要返回数据，并设置apdu缓冲区	
        //if(papdu.le != 0)
        // 如果成功，则返回数据，并且设置apdu缓冲区
        if( rc ) {
            Util.arrayCopyNonAtomic(papdu.pdata, (short)0, apdu_buffer, (short)5, (short)papdu.pdata.length);  
            apdu.setOutgoingAndSend((short)5, papdu.le);//把缓冲区的数据返回给终端  
        }  
	}

	/*
	 * 功能：对命令的分析和处理
	 * 参数：无
	 * 返回：是否成功处理了命令
	 */
	private boolean handleEvent(){
		switch(papdu.ins){
			case condef.INS_CREATE_FILE:   	    return create_file(); 	// E0 文件创建
			//todo：完成写二进制命令，读二进制命令，写密钥命令
            case (byte) 0xD4:  			        return write_key();  	// D4 写秘钥
            case (byte) 0xD6:          			return write_binary();  // D6 写二进制文件
            case (byte) 0xB0:          			return read_binary();  	// B0读二进制文件
            /*
            case condef.INS_NIIT_TRANS:  
                if(papdu.p1 == (byte)0x00)      return init_load();  
                if(papdu.p1 == (byte)0x01)      return init_purchase();  
                ISOException.throwIt(ISO7816.SW_WRONG_P1P2);//else抛出异常  
            case condef.INS_LOAD:               return load();  
            case condef.INS_PURCHASE:           return purchase();  
            case condef.INS_GET_BALANCE:        return get_balance(); 
            */
		}	
		ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED); // 0x6D00 表示 CLA错误 
		return false;
	}
	/*
	 * 功能：创建文件
	 */
	private boolean create_file() {
		// 判断DATA域文件控制信息（AEF）
		switch(papdu.pdata[0]){
		case condef.EP_FILE:        return EP_file();   	// 0x2F电子钱包文件
		//todo:完成创建密钥文件，持卡人基本文件和应用基本文件
        case (short)0x39:    		return Person_file(); 	// 0x39持卡人基本文件
        case (short)0x38:		    return APP_file();  	// 0x38应用基本文件
		case (short)0x3F:       	return Key_file();  	// 0x3F密钥文件
		default: 
			ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
		}
		return true;
	}
	/*
	 * 功能：创建电子钱包文件
	 * 样例，其他文件的实现方式类似
	 */
	private boolean EP_file() {
		// CLA：识别电子卡
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		// LC：Data Field之长度
		// 文件创建时文件信息长度为0x07
		if(papdu.lc != (byte)0x07)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		// 如果已经被分配
		if(EPfile != null)
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		
		EPfile = new EPFile(keyfile);
		
		return true;
	}	
	    
    /*
     * Key_file()
     * 密钥文件  
     * 仿照EP_file实现
     */
    private boolean Key_file() {

        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        // LC 应该为0x07, word中说的15有错
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(keyfile != null)
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        keyfile = new KeyFile();
  
        return true;  
    }  
    
    //创建应用基本文件  
    private boolean APP_file()  
    {  
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(cardfile != null)//有文件了还重复创建则会报错  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        if(keyfile == null)//都还没密钥文件（必须先于任何其他文件创建）  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        this.cardfile = new BinaryFile(papdu.pdata);//传进的参数就是要写入的内容  
  
        return true;  
    }  
    
    //创建持卡人信息文件  
    private boolean Person_file()  
    {  
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.lc != (byte)0x07)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(personfile != null)//有文件了还重复创建则会报错  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        if(keyfile == null)//都还没密钥文件（必须先于任何其他文件创建）  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        this.personfile = new BinaryFile(papdu.pdata);//传进的参数就是要写入的内容  
  
        return true;  
    }  
	
	//写入一条密钥  
    private boolean write_key()  
    {  
        if(keyfile == null)//都还没密钥文件  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        //文件标识有错,这句判断写得有问题啊,老是会返回这个异常--已解决，应该用and而不是or，因为要三个情况都不是才异常  
        if(papdu.p2 != (byte)0x06 && papdu.p2 != (byte)0x07 && papdu.p2 != (byte)0x08)  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);  
  
        if(papdu.lc == 0 || papdu.lc > 21)//密钥长度不能为0也不能超过21  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(keyfile.recNum >= 3)//文件空间已满  
            ISOException.throwIt(ISO7816.SW_FILE_FULL);  
  
        this.keyfile.addkey(papdu.p2, papdu.lc, papdu.pdata);//写入一条密钥  
  
        return true;  
    }  

    //写入二进制文件  
    private boolean write_binary()  
    {  
        if(keyfile == null)//都还没密钥文件  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        //都还没二进制文件--没找到  
        if(cardfile == null && papdu.p1 == (byte)0x16)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        if(personfile == null && papdu.p1 == (byte)0x17)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        /*if(papdu.p2 == 0)//没有文件标识 
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/  
  
        if(papdu.lc == 0)//写入长度不能为0  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        //写入一条二进制命令到文件  
        if(papdu.p1 == (byte)0x16)//表明写入的是应用信息  
            this.cardfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);  
        else if(papdu.p1 == (byte)0x17)//表明写入的是持卡人信息  
            this.personfile.write_bineary(papdu.p2, papdu.lc, papdu.pdata);  
  
        return true;  
    }  
    
    //读取二进制文件  
    private boolean read_binary()  
    {  
        if(keyfile == null)//都还没密钥文件  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        //都还没二进制文件--没找到  
        if(cardfile == null && papdu.p1 == (byte)0x16)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
        if(personfile == null && papdu.p1 == (byte)0x17)  
            ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);  
  
        if(papdu.cla != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        /*if(papdu.p2 == 0)//没有说明读取文件偏移量 
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);*/  
  
        //读取相应的二进文件  
        if(papdu.p1 == (byte)0x16)//表明读取的是应用文件  
            this.cardfile.read_binary(papdu.p2, papdu.le, papdu.pdata);  
        else if(papdu.p1 == (byte)0x17)//表明读取的是持卡人信息文件  
            this.personfile.read_binary(papdu.p2, papdu.le, papdu.pdata);  
  
        return true;  
    }  
    
    /*
	 * 功能：圈存命令的实现
	 */
	private boolean load() {
		short rc;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x00)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		rc = EPfile.load(papdu.pdata);
		
		if(rc == 1)//MAC2验证未通过  
			ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);
		else if(rc == 2)
			ISOException.throwIt(condef.SW_LOAD_FULL);
		else if(rc == 3)
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		papdu.le = (short)4;
		//papdu.le = (short)16; //正确为16
		
		return true;
	}

	/*
	 * 功能：圈存初始化命令的实现
	 */
	private boolean init_load() {
		short num,rc = 0;
		
		if(papdu.cla != (byte)0x80)
			ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);
		
		if(papdu.p1 != (byte)0x00 && papdu.p2 != (byte)0x02)
			ISOException.throwIt(ISO7816.SW_WRONG_P1P2);
		
		if(papdu.lc != (short)0x0B)
			ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
		
		if(EPfile == null)
			ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);
		
		num = keyfile.findkey(papdu.pdata[0]);
		
		
		if(num == 0x00) //表示找不到相应密钥 
			ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);
		
		rc = EPfile.init4load(num, papdu.pdata);//返回0表示成功,返回2表示超额  
		
		if(rc == 2)
			ISOException.throwIt((condef.SW_LOAD_FULL));		
		
		papdu.le = (short)0x10; // 似乎有问题？？
		
		return true;
	}
		/*
	 * 功能：消费命令的实现
	 */
	private boolean purchase(){
        short rc;  
        
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x00)  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);  
  
        if(EPfile == null)  
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        if(papdu.lc != (short)0x0F)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
            //测试所用//ISOException.throwIt(papdu.lc);  
  
        rc = EPfile.purchase(papdu.pdata);  
  
        if(rc == 1)//MAC1验证未通过  
            ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);  
        else if(rc == 2)  
            //ISOException.throwIt(condef.SW_BALANCE_NOT_ENOUGH);
        	ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);  
        else if(rc == 3)  
            ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);  
  
        papdu.le = (short)8;//正确是8  
        //papdu.le = (short)38;//测试  
		return true;
	}
	/*
	 * 功能：余额查询功能的实现
	 */
	private boolean get_balance(){
		if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x02)  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);  
  
        short result;  
        byte[] balance = JCSystem.makeTransientByteArray((short)4, JCSystem.CLEAR_ON_DESELECT);//余额暂存  
        result = EPfile.get_balance(balance);  
  
        if(result == (short)0)  
            Util.arrayCopyNonAtomic(balance, (short)0, papdu.pdata, (short)0, (short)4);//余额data[0]~data[3]  
  
        papdu.le = (short)0x04;  
		return true;
	}
	
	/*
	 * 功能：消费初始化的实现
	 */
	private boolean init_purchase(){
		short num,rc;  
		  
        if(papdu.cla != (byte)0x80)  
            ISOException.throwIt(ISO7816.SW_CLA_NOT_SUPPORTED);  
  
        if(papdu.p1 != (byte)0x01 && papdu.p2 != (byte)0x02)  
            ISOException.throwIt(ISO7816.SW_WRONG_P1P2);  
  
        if(papdu.lc != (short)0x0B)  
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);  
  
        if(EPfile == null)  
            ISOException.throwIt(ISO7816.SW_FILE_NOT_FOUND);  
  
        num = keyfile.findkey(papdu.pdata[0]);//根据tag寻找密钥返回密钥的记录号  
  
        if(num == 0x00)//表示找不到相应密钥  
            ISOException.throwIt(ISO7816.SW_RECORD_NOT_FOUND);  
  
        rc = EPfile.init4purchase(num, papdu.pdata);//返回0表示成功,返回2表示余额不足  
  
        if(rc == 2)  
            //ISOException.throwIt(condef.SW_BALANCE_NOT_ENOUGH);
        	ISOException.throwIt(ISO7816.SW_SECURITY_STATUS_NOT_SATISFIED);  
  
        papdu.le = (short)15;  
  
		return true;
	}
}

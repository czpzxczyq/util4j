package net.jueb.serializable.nobject.type;

import net.jueb.serializable.nobject.base.NObject;
import net.jueb.serializable.nobject.base.NObjectBase;
import net.jueb.serializable.nobject.base.P;
import net.jueb.serializable.nobject.flag.FlagEnd;
import net.jueb.serializable.nobject.flag.FlagHead;



public final class NUTF16LEString extends NObjectBase implements NObject{
	
	/**
	 * 维护的内部基础数据类型
	 */
	private String value;
	
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	/**
	 * 内存标记头
	 * 一个字节
	 */
	public static final byte flagHead=FlagHead.NUTF16LEString;
	/**
	 * 标记尾
	 * null表示无结尾或者直到遇到下一个head标记
	 */
	public static final byte[] flagEnd=FlagEnd.NUTF16LEString;
		
	/**
	 * 加上标记所占最小字节数
	 */
	public static final int minSize=3;
	
	
	
	@SuppressWarnings("unused")
	private NUTF16LEString() {
	}
	public NUTF16LEString(String arg) {
		this.value=arg;
	}
	
	public static NUTF16LEString valueOf(String arg)
	{
		return new NUTF16LEString(arg);
	}
	
	@Override
	public String value() {
		return this.value;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof NUTF16LEString)
		{
			NUTF16LEString obj1=(NUTF16LEString)obj;
			return value.equals(obj1.value());
		}
		else
		{
			return super.equals(obj);
		}
	}
	
	/**
	 * 获取该对象在内存中所表示的字节数组，包含标记头和位等信息
	 * flagHead + 数据 + flagEnd
	 * @return
	 */
	@Override
	public byte[] getBytes()
	{
		return hsb.addByteArray(hsb.addByteArray(new byte[]{flagHead}, (value).getBytes()), flagEnd);
	}
	@Override
	public byte[] valueBytes() {
		return this.value.getBytes();
	}	
	@Override
	public String toString() {
		return value().toString();
	}
	@Override
	public int hashCode() {
		return this.value.hashCode();
	}
	/**
	 * 工厂方式获取
	 * @param obj
	 * @return
	 */
	public static NUTF16LEString of(String obj) {
		return new NUTF16LEString(obj);
	}
	
	/**
	 * 从字节数组中P位置开始解析第一个符合的自身对象
	 * @param bytes
	 * @param p 起始位置
	 * @return 解析成功返回对象,且P会移出到第一个数据对象的区域 解析失败返回null
	 */
	public static NUTF16LEString of(byte[] bytes, P p)
	{
		//参数不符合要求
		if(!checkOfArgs(bytes, p, minSize))
		{
			return null;
		}
		if(canReadLength(bytes, p,1))
		{
			byte[] i=readByteArrayByLenght(bytes, p,1);
			if(i[0]!=flagHead)
			{//如果标记头不对则返回null
				return null;
			}else
			{
				byte[] data=readByteArrayByEndArray(bytes, p, flagEnd);
				byte[] value=hsb.subByteArray(data, 0, data.length-flagEnd.length-1);
				if(value!=null)
				{
					return new NUTF16LEString(new String(value));
				}
			}
		}
		return null;
	}
}
package com.jonathanaquino.svntimelapseview.scm;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ScmFactory {
	private static Map<String, Class<? extends ScmLoader>> registrations = new HashMap<String, Class<? extends ScmLoader>>();
	
	protected static void register(Class<? extends ScmLoader> loader)
	{
		registrations.put(KEY(loader), loader);
	}
	
	static {
		register(GitLoader.class);
		register(SvnLoader.class);
	}
	
	protected static String KEY(Class<? extends ScmLoader> cls) {
		try {
			Field f = cls.getField("KEY");
			Object v = f.get(null);
			return (String)v;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public static ScmLoader create(String key) throws ClassNotFoundException, IllegalAccessException, InstantiationException
	{
		Class<? extends ScmLoader> loader = registrations.get(key);
		if (loader == null)
			throw new ClassNotFoundException("Uknown scm loader " + key);
		
		return loader.newInstance();
	}
}

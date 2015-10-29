package be.nabu.libs.types.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import be.nabu.libs.types.api.DefinedType;
import be.nabu.libs.types.api.DefinedTypeResolver;

public class BeanResolver implements DefinedTypeResolver {

	private Map<String, DefinedType> resolved = new HashMap<String, DefinedType>();
	private Map<Class<?>, BeanType<?>> resolvedClasses = new HashMap<Class<?>, BeanType<?>>();
	
	/**
	 * This keeps track of which factory resolved which bean
	 * If they are unloaded, their beans are removed
	 */
	private Map<DomainObjectFactory, List<String>> factoryResolutions = new HashMap<DomainObjectFactory, List<String>>();
	
	private static BeanResolver instance;
	
	private List<DomainObjectFactory> objectFactories = new ArrayList<DomainObjectFactory>();
	
	public static BeanResolver getInstance() {
		if (instance == null) {
			instance = new BeanResolver();
		}
		return instance;
	}
	
	public BeanResolver() {
		if (instance == null) {
			instance = this;
		}
	}
		
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public DefinedType resolve(Class<?> clazz) {
		if (!resolvedClasses.containsKey(clazz)) {
			resolvedClasses.put(clazz, new BeanType(clazz));
		}
		return resolvedClasses.get(clazz);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public DefinedType resolve(String id) {
		if (!resolved.containsKey(id)) {
			synchronized(this) {
				if (!resolved.containsKey(id)) {
					Class<?> targetType = null;
					// first check domain object factories
					for (DomainObjectFactory factory : objectFactories) {
						try {
							targetType = factory.loadClass(id);
							if (targetType != null) {
								factoryResolutions.get(factory).add(id);
								break;
							}
						}
						catch (ClassNotFoundException e) {
							// ignore
						}
					}
					if (targetType == null) {
						try {
							// first try the classloader for this class, you may have enabled DynamicImport-Package
							// however that setting is useless if you don't use the classloader for this bundle
							targetType = getClass().getClassLoader().loadClass(id);
						}
						catch (ClassNotFoundException e) {
							// then try the thread classloader, it may be correct
							try {
								targetType = Thread.currentThread().getContextClassLoader().loadClass(id);
							}
							catch (ClassNotFoundException f) {
								return null;
							}
						}
					}
					BeanType<?> beanType = new BeanType(targetType);
					if (beanType.isSimpleType()) {
						resolved.put(id, new SimpleBeanType(beanType));
					}
					else {
						resolved.put(id, beanType);
					}
				}
			}
		}
		return resolved.get(id);
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void register(Class<?> clazz) {
		if (!resolvedClasses.containsKey(clazz)) {
			resolvedClasses.put(clazz, new BeanType(clazz));
		}
		if (!resolved.containsKey(clazz.getName())) {
			resolved.put(clazz.getName(), resolvedClasses.containsKey(clazz) ? resolvedClasses.get(clazz) : new BeanType(clazz));
		}
	}
	
	public synchronized void addFactory(DomainObjectFactory factory) {
		System.out.println("[" + this + " - " + this.getClass().getClassLoader() + "] Registering factory " + factory);
		factoryResolutions.put(factory, new ArrayList<String>());
		objectFactories.add(factory);
	}
	
	public synchronized void removeFactory(DomainObjectFactory factory) {
		for (String id : factoryResolutions.get(factory)) {
			resolved.remove(id);
		}
		objectFactories.remove(factory);
		factoryResolutions.remove(factoryResolutions);
	}
	
	@SuppressWarnings("unused")
	private void activate() {
		instance = this;
	}
	@SuppressWarnings("unused")
	private void deactivate() {
		instance = null;
	}
}

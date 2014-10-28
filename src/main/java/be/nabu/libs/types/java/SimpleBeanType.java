package be.nabu.libs.types.java;

import java.lang.reflect.Method;
import java.util.Iterator;

import be.nabu.libs.property.api.Value;
import be.nabu.libs.types.api.CollectionHandler;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SimpleType;

public class SimpleBeanType<T, S> extends BeanType<T> implements SimpleType<S> {

	private BeanType<T> beanType;
	
	public SimpleBeanType(BeanType<T> beanType) {
		this.beanType = beanType;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Class<S> getInstanceClass() {
		return ((SimpleType<S>) beanType.valueElement.getType()).getInstanceClass();
	}

	@Override
	public String getName(Value<?>... values) {
		return beanType.getName(values);
	}

	@Override
	public String getNamespace(Value<?>... values) {
		return beanType.getNamespace(values);
	}

	@Override
	public Iterator<Element<?>> iterator() {
		return beanType.iterator();
	}

	@Override
	public Element<?> get(String path) {
		if (path.equals(SIMPLE_TYPE_VALUE))
			return beanType.valueElement;
		else
			return beanType.get(path);
	}

	@Override
	public BeanInstance<T> newInstance() {
		return beanType.newInstance();
	}

	@Override
	public void setCollectionHandler(CollectionHandler handler) {
		beanType.setCollectionHandler(handler);
	}

	@Override
	public void unsetCollectionHandler(CollectionHandler handler) {
		beanType.unsetCollectionHandler(handler);
	}

	@Override
	public CollectionHandler getCollectionHandler() {
		return beanType.getCollectionHandler();
	}

	@Override
	Class<?> getActualType(String name) {
		return beanType.getActualType(name);
	}

	@Override
	public Class<T> getBeanClass() {
		return beanType.getBeanClass();
	}

	@Override
	public boolean isSimpleType() {
		return beanType.isSimpleType();
	}

	@Override
	protected String getIndicatedName(Method method) {
		return beanType.getIndicatedName(method);
	}

	@Override
	protected String getNamespace(Method method) {
		return beanType.getNamespace(method);
	}

	@Override
	protected boolean isElementQualified(Class<?> clazz) {
		return beanType.isElementQualified(clazz);
	}

	@Override
	protected boolean isNillable(Method method) {
		return beanType.isNillable(method);
	}

	@Override
	protected boolean isList(Method method) {
		return beanType.isList(method);
	}

	@Override
	protected boolean isAttributeQualified(Class<?> clazz) {
		return beanType.isAttributeQualified(clazz);
	}

	@Override
	protected boolean isAttribute(Method method) {
		return beanType.isAttribute(method);
	}

	@Override
	public ComplexType getSuperType() {
		return beanType.getSuperType();
	}

	@Override
	Method getSetter(String name) {
		if (name.equals(SIMPLE_TYPE_VALUE))
			return beanType.getSetter(beanType.valueElement.getName());
		else
			return beanType.getSetter(name);
	}

	@Override
	Method getGetter(String name) {
		if (name.equals(SIMPLE_TYPE_VALUE))
			return beanType.getGetter(beanType.valueElement.getName());
		else
			return beanType.getGetter(name);
	}

	@Override
	public String getId() {
		return beanType.getId();
	}
	
}

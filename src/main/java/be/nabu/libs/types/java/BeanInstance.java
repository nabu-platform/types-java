/*
* Copyright (C) 2014 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.types.java;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import be.nabu.libs.converter.ConverterFactory;
import be.nabu.libs.converter.api.Converter;
import be.nabu.libs.types.BaseTypeInstance;
import be.nabu.libs.types.CollectionHandlerFactory;
import be.nabu.libs.types.ComplexContentWrapperFactory;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.BeanConvertible;
import be.nabu.libs.types.api.CollectionHandler;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.DefinedSimpleType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.SneakyEditableBeanInstance;
import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.TypeInstance;
import be.nabu.libs.types.api.WrappedComplexContent;

/**
 * When you request a field from a bean instance and the resulting field is itself complex you could expect two things:
 * - the class as is described in the bean (ie the original value)
 * - a wrapped value that is itself a ComplexContent which wraps around the original value
 * 
 *  Depending on the context both are valid options but it is easier/less overhead to wrap manually than to unwrap manually
 *  Hence the tools are provided for easy wrapping but you need to do it yourself
 */
public class BeanInstance<T> implements BeanConvertible, WrappedComplexContent<T> {

	private BeanType<T> definition;
	private Object instance;
	private CollectionHandler handler;
	private TypeConverter simpleTypeConverter;
	private Converter converter;
	
	// introduce @18-11-2020, throwing a hard exception is annoying
	// at design time you shouldn't be accessing fields that don't exist unless you are doing something dynamic in which case the exception is seriously annoying
	// it is also not in sync with other types like structure which simply return null
	private static Boolean ignoreNonExistent = Boolean.parseBoolean(System.getProperty("bean.ignoreNonExistent", "true"));
	
	@SuppressWarnings({ "unchecked" })
	public BeanInstance(Object instance) {
		if (instance instanceof Class) {
			throw new IllegalArgumentException("Can not wrap around java.lang.Class");
		}
		if (Proxy.isProxyClass(instance.getClass())) {
			InvocationHandler invocationHandler = Proxy.getInvocationHandler(instance);
			if (invocationHandler instanceof BeanInterfaceInstance) {
				this.definition = (BeanType<T>) ((BeanInterfaceInstance) invocationHandler).getOriginalType();
			}
		}
		if (this.definition == null) {
			this.definition = (BeanType<T>) BeanResolver.getInstance().resolve((Class<T>) instance.getClass());
		}
		this.instance = instance;
	}
	
	public BeanInstance(BeanType<T> definition, Object instance) {
		if (definition == null) {
			throw new IllegalArgumentException("The definition of the instance " + instance + " can not be null");
		}
		this.definition = definition;
		this.instance = instance;
	}
	
	@Override
	public BeanType<T> getType() {
		return definition;
	}

	@Override
	public void set(String path, Object value) {
		set(ParsedPath.parse(path), value);
	}
	
	public void setConverter(Converter converter) {
		this.converter = converter;
	}
	public void unsetConverter(Converter converter) {
		this.converter = null;
	}
	public Converter getConverter() {
		if (converter == null)
			converter = ConverterFactory.getInstance().getConverter();
		return converter;
	}
	public void setSimpleTypeConverter(TypeConverter simpleTypeConverter) {
		this.simpleTypeConverter = simpleTypeConverter;
	}
	public void unsetSimpleTypeConverter(TypeConverter simpleTypeConverter) {
		this.simpleTypeConverter = null;
	}
	public TypeConverter getSimpleTypeConverter() {
		if (this.simpleTypeConverter == null)
			this.simpleTypeConverter = TypeConverterFactory.getInstance().getConverter();
		return this.simpleTypeConverter;
	}
	
	public void setCollectionHandler(CollectionHandler handler) {
		this.handler = handler;
	}
	public void unsetCollectionHandler(CollectionHandler handler) {
		this.handler = null;
	}
	public CollectionHandler getCollectionHandler() {
		if (this.handler == null)
			this.handler = CollectionHandlerFactory.getInstance().getHandler();
		return this.handler;
	}
	
	private void setValue(Object instance, String field, Object value) throws IllegalAccessException, InvocationTargetException {
		Method setter = getType().getSetter(field);
		if (setter == null) {
			if (instance instanceof SneakyEditableBeanInstance) {
				// we need to know the name of the getter to do a sneaky set
				Method getter = getType().getGetter(field);
				if (getter != null) {
					String name = getter.getName().startsWith("is") ? getter.getName().substring(2) : getter.getName().substring(3);
					name = name.substring(0, 1).toLowerCase() + name.substring(1);
					((SneakyEditableBeanInstance) instance).__set(name, value);
				}
				else {
					throw new RuntimeException("No getter found for field '" + field + "', no sneaky set possible");
				}
			}
			else {
				throw new RuntimeException("No setter found for field '" + field + "' and object '" + instance + "' (" + getType() + ") is not sneaky editable");
			}
		}
		else {
			setter.invoke(instance, value);
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void set(ParsedPath path, Object value) {
		boolean isAttribute = path.getName().startsWith("@");
		String pathName = isAttribute ? path.getName().substring(1) : path.getName();
		Element<?> definition = getType().get(pathName);
		
		if (definition == null)
			throw new IllegalArgumentException("The field " + pathName + " does not exist in " + getType().getName());
		if (path.getIndex() != null && !definition.getType().isList(definition.getProperties()))
			throw new IllegalArgumentException("The field " + pathName + " is not a list");
		if (path.getChildPath() != null && !(definition.getType() instanceof ComplexType))
			throw new IllegalArgumentException("The field " + pathName + " is not a complex type");
		
		try {
			// we are working with a specific field in a list
			if (path.getIndex() != null) {
				Class<?> actualType = getType().getActualType(pathName);
				CollectionHandlerProvider collectionHandler = getCollectionHandler().getHandler(actualType);
				if (collectionHandler == null)
					throw new IllegalArgumentException("Can not access the object " + pathName);
				// get the current value
				Object listObject = getType().getGetter(pathName).invoke(instance);
				Object parsedIndex = collectionHandler.unmarshalIndex(path.getIndex(), listObject);
				// does not yet exist, we need to initialize it
				if (listObject == null && (CREATE_PARENT_FOR_NULL_VALUE || value != null)) {
					// the size only matters if it is integer-based index
					listObject = collectionHandler.create(actualType, parsedIndex instanceof Integer ? ((Integer) parsedIndex) + 1 : 1);
					// set it in the object
					setValue(instance, pathName, listObject);
				}
				if (listObject != null) {
					// we need to update locally
					if (path.getChildPath() == null) {
						value = convert(value, collectionHandler.getComponentType(getType().getGenericType(pathName)), definition);
						setValue(instance, pathName, collectionHandler.set(listObject, parsedIndex, value));
					}
					// otherwise we need to recurse
					else {
						Object singleObject = collectionHandler.get(listObject, parsedIndex);
						if (singleObject == null && (CREATE_PARENT_FOR_NULL_VALUE || value != null)) {
//							singleObject = getType().getActualType(pathName).newInstance();
							// this makes sure we can dynamically generate proxies etc
							singleObject = new BeanType(collectionHandler.getComponentType(getType().getGenericType(pathName))).newInstance().getUnwrapped();
							collectionHandler.set(listObject, parsedIndex, singleObject);
						}
						if (singleObject != null) {
							if (!(singleObject instanceof ComplexContent))
								singleObject = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(singleObject);
							((ComplexContent) singleObject).set(path.getChildPath().toString(), value);
						}
					}
				}
			}
			// just update the field
			else if (path.getChildPath() == null) {
				value = convert(value, getType().getActualType(pathName), definition);
				setValue(instance, pathName, value);
			}
			else {
				// we need to recurse
				Object singleObject = getType().getGetter(pathName).invoke(instance);
				if (singleObject == null && (CREATE_PARENT_FOR_NULL_VALUE || value != null)) {
					// this supports interfaces!
					singleObject = new BeanType(getType().getActualType(pathName)).newInstance().getUnwrapped();
//					singleObject = getType().getActualType(pathName).newInstance();
					setValue(instance, pathName, singleObject);
				}
				if (singleObject != null) {
					if (!(singleObject instanceof ComplexContent))
						singleObject = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(singleObject);
					((ComplexContent) singleObject).set(path.getChildPath().toString(), value);
				}
			}
		}
		catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		} 
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private Object convert(Object value, Class<?> targetClass, Element<?> definition) {
		if (value == null)
			return null;
		// we can't "convert" to object, it just accepts everything...
		else if (Object.class.equals(targetClass)) {
			return value;
		}
		Class<?> originalClass = value.getClass();
		Object converted = null;
		// if it is complex content, we might be able to proxy it
		if (value instanceof ComplexContent) {
			converted = TypeUtils.getAsBean((ComplexContent) value, targetClass);
		}
		else if (targetClass.isAssignableFrom(value.getClass())) {
			converted = value;
		}
		// this logic is slightly out of sync with structure instance logic
		else {
			// need to wrap class
			DefinedSimpleType<? extends Object> wrap = SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(value.getClass());
			if (wrap == null) {
				converted = ConverterFactory.getInstance().getConverter().convert(value, targetClass);
			}
			else {
				TypeInstance targetType = new BaseTypeInstance(wrap);
				converted = TypeConverterFactory.getInstance().getConverter().convert(value, targetType, definition);
			}
		}
		if (converted == null && !definition.getType().isList(definition.getProperties())) {
			CollectionHandlerProvider handler = CollectionHandlerFactory.getInstance().getHandler().getHandler(value.getClass());
			if (handler != null) {
				Collection collection = handler.getAsCollection(value);
				if (collection.size() == 1) {
					Object next = collection.iterator().next();
					return convert(next, targetClass, definition);
				}
				else if (collection.size() == 0) {
					return null;
				}
				else {
					throw new IllegalArgumentException("The non-empty collection '" + value + "' for field '" + definition.getName() + "' can not be converted from " + originalClass + " to the single item of type " + targetClass);		
				}
			}
		}
		// if we have an iterable on one side and a java array on the other, try conversion
		if (value instanceof Iterable && Object[].class.isAssignableFrom(targetClass)) {
			// even if it is already a collection, we want to ensure object-compatibility before we contruct an array around it
			Class<?> componentType = targetClass.getComponentType();
			List result = new ArrayList();
			// TODO: do we need to create a clone for the definition element and make it singular instead of a list?
			for (Object single : (Iterable) value) {
				result.add(convert(single, componentType, definition));
			}
			converted = result.toArray((Object[]) java.lang.reflect.Array.newInstance(componentType, result.size()));
		}
		if (converted == null)
			throw new IllegalArgumentException("The value can not be converted from " + originalClass + " to " + targetClass);
		return converted;
	}
	
	@Override
	public Object get(String path) {
		return get(ParsedPath.parse(path));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	Object get(ParsedPath path) {
		boolean isAttribute = path.getName().startsWith("@");
		String pathName = isAttribute ? path.getName().substring(1) : path.getName();
		Element<?> definition = getType().get(pathName);

		if (definition == null) {
			if (ignoreNonExistent) {
				return null;
			}
			else {
				throw new IllegalArgumentException("The field " + pathName + " does not exist in " + getType().getBeanClass().getName());
			}
		}
		if (path.getIndex() != null && !definition.getType().isList(definition.getProperties()))
			throw new IllegalArgumentException("The field " + pathName + " is not a list");
		if (path.getChildPath() != null && !(definition.getType() instanceof ComplexType))
			throw new IllegalArgumentException("The field " + pathName + " is not a complex type");
		
		Method getter = getType().getGetter(pathName);
		try {
			if (!getter.isAccessible()) {
				getter.setAccessible(true);
			}
			Object object = getter.getParameterTypes().length == 1
				? getter.invoke(instance, new Object[] { Array.newInstance((Class<?>) getter.getParameterTypes()[0].getComponentType(), 0) }) 
				: getter.invoke(instance);
			if (path.getIndex() != null) {
				CollectionHandlerProvider collectionHandler = getCollectionHandler().getHandler(object.getClass());
				if (collectionHandler == null)
					throw new IllegalArgumentException("Can not access the object " + pathName);
				Object parsedIndex = collectionHandler.unmarshalIndex(path.getIndex(), object);
				object = collectionHandler.get(object, parsedIndex);
			}
			// we just need the field
			if (path.getChildPath() == null || object == null)
				return object;
			else if (object instanceof BeanInstance)
				return ((BeanInstance<?>) object).get(path.getChildPath());
			else
				return new BeanInstance((BeanType<?>) definition.getType(), object).get(path.getChildPath());
		}
		catch (InvocationTargetException e) {
			throw new RuntimeException("Can not access path '" + path + "' in " + getUnwrapped().getClass() + " => " + getter, e);
		} 
		catch (IllegalAccessException e) {
			throw new RuntimeException("Can not access path '" + path + "' in " + getUnwrapped().getClass() + " => " + getter, e);
		}
		catch (RuntimeException e) {
			throw new RuntimeException("Can not access path '" + path + "' in " + getUnwrapped().getClass() + " using " + getter.getDeclaringClass().getClassLoader() + " on " + instance.getClass().getClassLoader(), e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public <S> S asBean(Class<S> target) {
		return (S) (target.isAssignableFrom(instance.getClass()) ? instance : null);
	}
	
	@SuppressWarnings("unchecked")
	public static <A> A unwrap(Object e, Class<A> classType) {
		if (e == null)
			return null;
		else if (classType.isAssignableFrom(e.getClass()))
			return (A) e;
		else if (e instanceof BeanConvertible)
			return ((BeanConvertible) e).asBean(classType);
		else
			throw new IllegalArgumentException("The parameter can not be unwrapped to the proper component");
	}
	
	@SuppressWarnings("rawtypes")
	public static ComplexContent wrap(Object e) {
		if (e == null)
			return null;
		else
			return new BeanInstance(e);
	}
	
	public static class ComplexContentList<T> implements List<Object> {

		private Class<T> classType;
		private List<T> list;
		
		public ComplexContentList(Class<T> classType, List<T> list) {
			this.classType = classType;
			this.list = list == null ? new ArrayList<T>() : list;
		}

		private T unwrap(Object e) {
			return BeanInstance.unwrap(e, classType);
		}
		
		private ComplexContent wrap(Object e) {
			return BeanInstance.wrap(e);
		}
		
		private Collection<T> unwrap(Collection<? extends Object> c) {
			List<T> list = new ArrayList<T>();
			for (Object o : c)
				list.add(unwrap(o));
			return list;
		}
		
		@Override
		public boolean add(Object e) {
			return list.add(unwrap(e));
		}

		@Override
		public void add(int index, Object element) {
			list.add(index, unwrap(element));
		}


		@Override
		public boolean addAll(Collection<? extends Object> c) {
			return list.addAll(unwrap(c));
		}

		@Override
		public boolean addAll(int index, Collection<? extends Object> c) {
			return list.addAll(index, unwrap(c));
		}

		@Override
		public void clear() {
			list.clear();
		}

		@Override
		public boolean contains(Object o) {
			return list.contains(unwrap(o));
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return list.containsAll(unwrap(c));
		}

		@Override
		public Object get(int index) {
			return wrap(list.get(index));
		}

		@Override
		public int indexOf(Object o) {
			return list.indexOf(unwrap(o));
		}

		@Override
		public boolean isEmpty() {
			return list.isEmpty();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public Iterator<Object> iterator() {
			return new ComplexContentIterator(list.iterator());
		}

		@Override
		public int lastIndexOf(Object o) {
			return list.lastIndexOf(unwrap(o));
		}

		@Override
		public ListIterator<Object> listIterator() {
			throw new UnsupportedOperationException ();
		}

		@Override
		public ListIterator<Object> listIterator(int index) {
			throw new UnsupportedOperationException ();
		}

		@Override
		public boolean remove(Object o) {
			return list.remove(unwrap(o));
		}

		@Override
		public Object remove(int index) {
			return wrap(list.remove(index));
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			return list.removeAll(unwrap(c));
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			return list.retainAll(unwrap(c));
		}

		@Override
		public Object set(int index, Object element) {
			return wrap(list.set(index, unwrap(element)));
		}

		@Override
		public int size() {
			return list.size();
		}

		@SuppressWarnings({ "unchecked", "rawtypes" })
		@Override
		public List<Object> subList(int fromIndex, int toIndex) {
			return new ComplexContentList(classType, list.subList(fromIndex, toIndex));
		}

		@Override
		public Object[] toArray() {
			return internalToArray(null);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <A> A[] toArray(A[] a) {
			return internalToArray(a);
		}
		
		// mismatches in later versions
		private <A> A[] internalToArray(A[] a) {
			A [] array = a != null && a.length >= list.size() ? a : (A[]) Array.newInstance(classType, list.size());
			for (int i = 0; i < list.size(); i++)
				array[i] = (A) list.get(i);
			return array;
		}
		
	}
	
	public static class ComplexContentIterator<T> implements Iterator<ComplexContent> {

		private Iterator<T> original;
		
		public ComplexContentIterator(Iterator<T> original) {
			this.original = original;
		}
		
		@Override
		public boolean hasNext() {
			return original.hasNext();
		}

		@Override
		public ComplexContent next() {
			return BeanInstance.wrap(original.next());
		}

		@Override
		public void remove() {
			original.remove();
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public T getUnwrapped() {
		return (T) instance;
	}
}

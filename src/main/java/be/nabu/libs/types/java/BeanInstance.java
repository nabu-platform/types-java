package be.nabu.libs.types.java;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import be.nabu.libs.types.DefinedTypeResolverFactory;
import be.nabu.libs.types.ParsedPath;
import be.nabu.libs.types.SimpleTypeWrapperFactory;
import be.nabu.libs.types.TypeConverterFactory;
import be.nabu.libs.types.TypeUtils;
import be.nabu.libs.types.api.Attribute;
import be.nabu.libs.types.api.BeanConvertible;
import be.nabu.libs.types.api.CollectionHandler;
import be.nabu.libs.types.api.CollectionHandlerProvider;
import be.nabu.libs.types.api.ComplexContent;
import be.nabu.libs.types.api.ComplexType;
import be.nabu.libs.types.api.Element;
import be.nabu.libs.types.api.TypeConverter;
import be.nabu.libs.types.api.TypeInstance;

/**
 * When you request a field from a bean instance and the resulting field is itself complex you could expect two things:
 * - the class as is described in the bean (ie the original value)
 * - a wrapped value that is itself a ComplexContent which wraps around the original value
 * 
 *  Depending on the context both are valid options but it is easier/less overhead to wrap manually than to unwrap manually
 *  Hence the tools are provided for easy wrapping but you need to do it yourself
 */
public class BeanInstance<T> implements ComplexContent, BeanConvertible {

	private BeanType<T> definition;
	private Object instance;
	private CollectionHandler handler;
	private TypeConverter simpleTypeConverter;
	private Converter converter;

	@SuppressWarnings({ "unchecked" })
	public BeanInstance(Object instance) {
		if (instance instanceof Class) {
			throw new IllegalArgumentException("Can not wrap around java.lang.Class");
		}
		this.definition = (BeanType<T>) DefinedTypeResolverFactory.getInstance().getResolver().resolve(instance.getClass().getName());
		// it can not be generally resolved, do a local resolve
		// TODO: we should move the "statically resolved" from the BeanResolver into the BeanType class
		// this will allow easy reuse, now the only downside is the repeated reflection
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
		set(new ParsedPath(path), value);
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
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	void set(ParsedPath path, Object value) {
		boolean isAttribute = path.getName().startsWith("@");
		String pathName = isAttribute ? path.getName().substring(1) : path.getName();
		Element<?> definition = getType().get(pathName);
		if (isAttribute && !(definition instanceof Attribute)) {
			throw new IllegalArgumentException("The field " + pathName + " is not an attribute");
		}
		
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
				Object parsedIndex = collectionHandler.unmarshalIndex(path.getIndex());
				// does not yet exist, we need to initialize it
				if (listObject == null) {
					// the size only matters if it is integer-based index
					listObject = collectionHandler.create(actualType, parsedIndex instanceof Integer ? ((Integer) parsedIndex) + 1 : 1);
					// set it in the object
					getType().getSetter(pathName).invoke(instance, listObject);
				}				
				// we need to update locally
				if (path.getChildPath() == null) {
					value = convert(value, collectionHandler.getComponentType(getType().getGenericType(pathName)), definition);
					Method setter = getType().getSetter(pathName);
					if (setter == null) {
						throw new IllegalArgumentException("There is no setter for " + pathName + " in " + getType().getBeanClass());
					}
					setter.invoke(instance,
						collectionHandler.set(listObject, parsedIndex, value));
				}
				// otherwise we need to recurse
				else {
					Object singleObject = collectionHandler.get(listObject, parsedIndex);
					if (!(singleObject instanceof ComplexContent))
						singleObject = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(singleObject);
					((ComplexContent) singleObject).set(path.getChildPath().toString(), value);
				}
			}
			// just update the field
			else if (path.getChildPath() == null) {
				value = convert(value, getType().getActualType(pathName), definition);
				Method setter = getType().getSetter(pathName);
				if (setter == null) {
					throw new IllegalArgumentException("No setter found for field: " + pathName);
				}
				setter.invoke(instance, value);
			}
			else {
				// we need to recurse
				Object singleObject = getType().getGetter(pathName).invoke(instance);
				if (singleObject == null) {
					singleObject = getType().getActualType(pathName).newInstance();
					getType().getSetter(pathName).invoke(instance, singleObject);
				}
				if (!(singleObject instanceof ComplexContent))
					singleObject = ComplexContentWrapperFactory.getInstance().getWrapper().wrap(singleObject);
				((ComplexContent) singleObject).set(path.getChildPath().toString(), value);
			}
		}
		catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		} 
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		catch (InstantiationException e) {
			throw new RuntimeException(e);
		}
	}

	private Object convert(Object value, Class<?> targetClass, Element<?> definition) {
		if (value == null)
			return null;
		Class<?> originalClass = value.getClass();
		// if it is complex content, we might be able to proxy it
		if (value instanceof ComplexContent) {
			value = TypeUtils.getAsBean((ComplexContent) value, targetClass);
		}
		else if (!targetClass.isAssignableFrom(value.getClass())) {
			// need to wrap class
			TypeInstance targetType = new BaseTypeInstance(SimpleTypeWrapperFactory.getInstance().getWrapper().wrap(value.getClass()));
			value = TypeConverterFactory.getInstance().getConverter().convert(value, targetType, definition);
		}
		if (value == null)
			throw new IllegalArgumentException("The value can not be converted from " + originalClass + " to " + targetClass);
		return value;
	}
	
	@Override
	public Object get(String path) {
		return get(new ParsedPath(path));
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	Object get(ParsedPath path) {
		boolean isAttribute = path.getName().startsWith("@");
		String pathName = isAttribute ? path.getName().substring(1) : path.getName();
		Element<?> definition = getType().get(pathName);
		if (isAttribute && !(definition instanceof Attribute)) {
			throw new IllegalArgumentException("The field " + pathName + " is not an attribute");
		}
		if (definition == null)
			throw new IllegalArgumentException("The field " + pathName + " does not exist in " + getType().getName());
		if (path.getIndex() != null && !definition.getType().isList(definition.getProperties()))
			throw new IllegalArgumentException("The field " + pathName + " is not a list");
		if (path.getChildPath() != null && !(definition.getType() instanceof ComplexType))
			throw new IllegalArgumentException("The field " + pathName + " is not a complex type");
		
		try {
			Object object = getType().getGetter(pathName).invoke(instance);
			if (path.getIndex() != null) {
				CollectionHandlerProvider collectionHandler = getCollectionHandler().getHandler(object.getClass());
				if (collectionHandler == null)
					throw new IllegalArgumentException("Can not access the object " + pathName);
				Object parsedIndex = collectionHandler.unmarshalIndex(path.getIndex());
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
			throw new RuntimeException(e);
		} 
		catch (IllegalAccessException e) {
			throw new RuntimeException(e);
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
			return toArray(null);
		}

		@SuppressWarnings("unchecked")
		@Override
		public <A> A[] toArray(A[] a) {
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
	public T getInstance() {
		return (T) instance;
	}
}

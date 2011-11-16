/**
 *  Copyright 2003-2010 Terracotta, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package net.sf.ehcache.jcache;


import net.sf.ehcache.CacheException;
import net.sf.ehcache.Element;
import net.sf.ehcache.store.compound.ReadWriteCopyStrategy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

/**
 * This class provides a copy strategy that is compatible with JSR107's requirement that
 * storeByValue caches will store the keys by value as well as the values.
 * <p/>
 * Once ehcache handles these concerns natively, this class will be either deleted or
 * deprecated.
 * <p/>
 * (in other words, you should not use this class directly)
 * <p/>
 * (some code was copied from  net.sf.ehcache.store.compound.ReadWriteSerializationCopyStrategy which was written by
 *
 * @author Alex Snaps
 * @author Ludovic Orban
 *         )
 * @author Ryan Gardner
 * @since 0.4
 */
public class JCacheCopyOnWriteStrategy implements ReadWriteCopyStrategy<Element> {
    private ClassLoader deserializationClassLoader;

    public JCacheCopyOnWriteStrategy(ClassLoader deserializationClassLoader) {
        this.deserializationClassLoader = deserializationClassLoader;
    }

    /**
     * Deep copies some object and returns an internal storage-ready copy
     *
     * @param value the value to copy
     * @return the storage-ready copy
     */
    @Override
    public Element copyForWrite(Element value) {
        if (value == null) {
            return null;
        } else {
            Object elementValue = value.getObjectValue();
            Object elementKey = value.getKey();

            byte[] serializedValue = cloneObjectToByteArray(elementValue);

            return duplicateElementWithNewValue(value, serializedValue);
        }
    }

    byte[] cloneObjectToByteArray(Object elementValue) {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oos = null;

        try {
            oos = new ObjectOutputStream(bout);
            oos.writeObject(elementValue);
        } catch (Exception e) {
            throw new CacheException("When configured copyOnRead or copyOnWrite, a Store will only accept Serializable values", e);
        } finally {
            try {
                if (oos != null) {
                    oos.close();
                }
            } catch (Exception e) {
                //
            }
        }
        return bout.toByteArray();
    }


    /**
     * Reconstruct an object from its storage-ready copy.
     *
     * @param storedValue the storage-ready copy
     * @return the original object
     */
    @Override
    public Element copyForRead(Element storedValue) {
        if (storedValue == null) {
            return null;
        } else {
            Object deserializedElement = deserializeByteArrayToObject((byte[]) storedValue.getValue());

            return duplicateElementWithNewValue(storedValue, deserializedElement);
        }
    }

    Object deserializeByteArrayToObject(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = null;
        Object deserializedElement;
        try {
            ois = new PreferredClassLoaderObjectInputSteam(bin, this.deserializationClassLoader);
            deserializedElement = ois.readObject();
        } catch (Exception e) {
            throw new CacheException("When configured copyOnRead or copyOnWrite, a Store will only accept Serializable values", e);
        } finally {
            try {
                if (ois != null) {
                    ois.close();
                }
            } catch (Exception e) {
                //
            }
        }
        return deserializedElement;
    }

    /**
     * Make a duplicate of an element but using the specified value
     *
     * @param element  the element to duplicate
     * @param newValue the new element's value
     * @return the duplicated element
     * @see net.sf.ehcache.store.compound.ReadWriteSerializationCopyStrategy#duplicateElementWithNewValue(net.sf.ehcache.Element, Object)
     */
    protected Element duplicateElementWithNewValue(final Element element, final Object newValue) {
        return new Element(element.getKey(), newValue, element.getVersion(),
                element.getCreationTime(), element.getLastAccessTime(), element.getHitCount(), element.usesCacheDefaultLifespan(),
                element.getTimeToLive(), element.getTimeToIdle(), element.getLastUpdateTime());
    }

    /**
     * This class provides a way to satisfy the requirements of JSR107 in being able to specify a classloader to deserialize
     * objects with
     */
    private static class PreferredClassLoaderObjectInputSteam extends ObjectInputStream {
        private ClassLoader classLoader;

        /**
         * Constructor
         *
         * @param in
         * @throws IOException
         */
        public PreferredClassLoaderObjectInputSteam(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {

            return Class.forName(desc.getName(), false, classLoader);

        }

    }

}
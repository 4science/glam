/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.util.ReflectionUtils;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class ChecksumFieldRowMapper implements ChecksumRowMapper {

    private final Field field;

    public ChecksumFieldRowMapper(String field) {
        this.field = ReflectionUtils.findField(MostRecentChecksum.class, field);
    }

    @Override
    public List<String> apply(MostRecentChecksum checksum) {
        boolean accessible = this.field.isAccessible();
        this.field.setAccessible(true);
        try {
            Object o = this.field.get(checksum);
            if (isCollection(o)) {
                return mapToString((Collection<?>) o);
            }
            if (isMap(o)) {
                return mapToString(((Map<?, ?>) o).values());
            }
            if (!(o instanceof String)) {
                return List.of(String.valueOf(o));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } finally {
            this.field.setAccessible(accessible);
        }
        return List.of();
    }

    public static List<String> mapToString(Collection<?> col) {
        return col.stream().map(String::valueOf).collect(Collectors.toList());
    }

    public static boolean isCollection(Object o) {
        return  (o instanceof Collection<?>);
    }

    public static boolean isMap(Object o) {
        return  (o instanceof Map<?,?>);
    }
}

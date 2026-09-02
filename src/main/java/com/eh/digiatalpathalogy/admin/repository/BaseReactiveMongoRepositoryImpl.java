package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import com.mongodb.client.result.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Objects;
import java.util.Set;

@Repository
public class BaseReactiveMongoRepositoryImpl<T> implements BaseReactiveMongoRepository<T> {

    private final ReactiveMongoTemplate mongoTemplate;
    private static final String ERR_QUERY_NO_NULL = "query must not be null";
    private static final String ERR_UPDATE_NO_NULL = "update must not be null";
    private static final String ERR_CLASS_NO_NULL = "class must not be null";
    private static final String ERR_SOURCE_NO_NULL = "source must not be null";

    @Autowired
    public BaseReactiveMongoRepositoryImpl(ReactiveMongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Mono<T> findAndModify(Query query, Update update, Class<T> clazz) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(update, ERR_UPDATE_NO_NULL);
        Objects.requireNonNull(clazz, ERR_CLASS_NO_NULL);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(false),
                clazz);
    }

    @Override
    public Mono<T> findAndModify(Query query, T source) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(source, ERR_SOURCE_NO_NULL);
        Update update = buildUpdate(source);
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) source.getClass();
        return findAndModify(query, update, clazz);
    }

    @Override
    public Mono<T> findAndModify(Query query, T source, boolean includeNulls) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(source, ERR_SOURCE_NO_NULL);
        Update update = buildUpdate(source, true, includeNulls);
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) source.getClass();
        return findAndModify(query, update, clazz);
    }

    @Override
    public Mono<T> saveOrUpdate(Query query, T source) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(source, ERR_SOURCE_NO_NULL);
        Update update = buildUpdate(source);
        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) source.getClass();
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                clazz);

    }

    @Override
    public Mono<T> saveOrUpdate(Query query, Update update, Class<T> clazz) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(update, ERR_UPDATE_NO_NULL);
        Objects.requireNonNull(clazz, ERR_CLASS_NO_NULL);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true).upsert(true),
                clazz);
    }

    @Override
    public Mono<Long> updateMany(Query query, Update update, Class<T> clazz) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(update, ERR_UPDATE_NO_NULL);
        Objects.requireNonNull(clazz, ERR_CLASS_NO_NULL);
        return mongoTemplate
                .updateMulti(query, update, clazz)
                .map(UpdateResult::getModifiedCount);
    }

    @Override
    public Mono<Long> updateMany(Query query, T source) {
        Objects.requireNonNull(query, ERR_QUERY_NO_NULL);
        Objects.requireNonNull(source, ERR_SOURCE_NO_NULL);

        Update update = buildUpdate(source);

        @SuppressWarnings("unchecked")
        Class<T> clazz = (Class<T>) source.getClass();

        return updateMany(query, update, clazz);
    }

    public Update buildUpdate(Object source, boolean trimStrings) {
        return buildUpdate(source, trimStrings, false);
    }

    public Update buildUpdate(Object source, boolean trimStrings, boolean includeNulls) {
        Objects.requireNonNull(source, ERR_SOURCE_NO_NULL);
        Class<?> type = source.getClass();
        return type.isRecord() ? buildFromRecord(source, trimStrings, includeNulls) : buildFromPojo(source, trimStrings, includeNulls);
    }

    public Update buildUpdate(Object source) {
        return buildUpdate(source, true);
    }

    private Update buildFromRecord(Object patch, boolean trimStrings, boolean includeNulls) {
        Update update = new Update();
        RecordComponent[] components = patch.getClass().getRecordComponents();

        for (RecordComponent rc : components) {
            String field = rc.getName();

            Object value;
            try {
                Method accessor = rc.getAccessor();
                value = accessor.invoke(patch);
            } catch (ReflectiveOperationException e) {
                throw new InternalServerException("Failed to access record component: " + field, e);
            }
            maybeSet(update, field, value, trimStrings, includeNulls);
        }
        return update;
    }

    private Update buildFromPojo(Object patch, boolean trimStrings, boolean includeNulls) {
        Update update = new Update();
        try {
            for (PropertyDescriptor pd : Introspector.getBeanInfo(patch.getClass(), Object.class).getPropertyDescriptors()) {
                Method reader = pd.getReadMethod();
                if (reader == null)
                    continue;

                String field = pd.getName();
                Object value = reader.invoke(patch);

                maybeSet(update, field, value, trimStrings, includeNulls);
            }
        } catch (Exception e) {
            throw new InternalServerException("Failed to introspect POJO: " + patch.getClass().getName(), e);
        }
        return update;
    }

    private static final Set<String> NEVER_NULL_FIELDS = Set.of("id", "deviceSerialNumber");

    private void maybeSet(Update update, String field, Object value, boolean trimStrings, boolean includeNulls) {
        if (value == null) {
            if (includeNulls && !NEVER_NULL_FIELDS.contains(field)) {
                update.set(field, null);
            }
            return;
        }

        if (value instanceof String s) {
            if (!StringUtils.hasText(s))
                return;
            update.set(field, trimStrings ? s.trim() : s);
        } else {
            update.set(field, value);
        }
    }

}

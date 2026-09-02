package com.eh.digiatalpathalogy.admin.repository;

import com.eh.digiatalpathalogy.admin.exception.InternalServerException;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.core.query.UpdateDefinition;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BaseReactiveMongoRepositoryImplTest {

    @Mock
    ReactiveMongoTemplate mongoTemplate;

    @InjectMocks
    private BaseReactiveMongoRepositoryImpl<Object> repo;

    static class PatchPojo {
        private final String name;
        private final String blank;
        private final Integer age;

        PatchPojo(String name, String blank, Integer age) {
            this.name = name;
            this.blank = blank;
            this.age = age;
        }

        public String getName() { return name; }
        public String getBlank() { return blank; }
        public Integer getAge() { return age; }
    }

    static class BadPojo {
        public String getName() {
            throw new RuntimeException("boom");
        }
    }

    record PatchRecord(String name, String blank, Integer age) { }

    record PatchWithIdRecord(String id, String deviceSerialNumber, String name) { }

    record BadRecord(String name) {
        @Override public String name() { throw new RuntimeException("boom"); }
    }

    // --- Helpers -------------------------------------------------------------

    private static Document setDoc(UpdateDefinition update) {
        Document updateDoc = update.getUpdateObject();
        Object set = updateDoc.get("$set");
        return (set instanceof Document d) ? d : new Document();
    }

    private static ArgumentMatcher<FindAndModifyOptions> optionsReturnNewTrueUpsertFalse() {
        return opts -> opts != null && opts.isReturnNew() && !opts.isUpsert();
    }

    private static ArgumentMatcher<FindAndModifyOptions> optionsReturnNewTrueUpsertTrue() {
        return opts -> opts != null && opts.isReturnNew() && opts.isUpsert();
    }

    private static ArgumentMatcher<Query> emptyQuery() {
        return q -> q != null
                && q.getQueryObject().isEmpty()
                && q.getFieldsObject().isEmpty()
                && q.getSortObject().isEmpty();
    }

    // --- Tests ---------------------------------------------------------------

    @Nested
    class FindAndModifyWithExplicitUpdate {

        @Test
        @DisplayName("findAndModify(query, update, clazz): null query → NPE")
        void findAndModify_nullQuery_throwsNpe() {
            assertThatThrownBy(() -> repo.findAndModify(null, new Update().set("x", 1), Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("query must not be null");
        }

        @Test
        @DisplayName("findAndModify(query, update, clazz): null update → NPE")
        void findAndModify_nullUpdate_throwsNpe() {
            assertThatThrownBy(() -> repo.findAndModify(new Query(), null, Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("update must not be null");
        }

        @Test
        @DisplayName("findAndModify(query, update, clazz): null clazz → NPE")
        void findAndModify_nullClazz_throwsNpe() {
            assertThatThrownBy(() -> repo.findAndModify(new Query(), new Update().set("x", 1), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("class must not be null");
        }

        @Test
        @DisplayName("findAndModify(query, update, clazz): delegates to template with returnNew=true and upsert=false")
        void findAndModify_delegatesToTemplate_withCorrectOptions() {
            Query query = new Query();
            Update update = new Update().set("name", "abc");
            Object expected = new Object();

            when(mongoTemplate.findAndModify(
                    argThat(emptyQuery()),        // <- avoid identity matcher
                    eq(update),
                    argThat(optionsReturnNewTrueUpsertFalse()),
                    eq(Object.class)
            )).thenReturn(Mono.just(expected));

            StepVerifier.create(repo.findAndModify(query, update, Object.class))
                    .expectNext(expected)
                    .verifyComplete();

            verify(mongoTemplate, times(1)).findAndModify(
                    argThat(emptyQuery()),
                    any(Update.class),
                    argThat(optionsReturnNewTrueUpsertFalse()),
                    eq(Object.class)
            );
            verifyNoMoreInteractions(mongoTemplate);
        }
    }

    @Nested
    class FindAndModifyWithSourceObject {

        @Test
        @DisplayName("findAndModify(query, source): null query → NPE")
        void findAndModifySource_nullQuery_throwsNpe() {
            assertThatThrownBy(() -> repo.findAndModify(null, new PatchPojo("test", null, 1)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("query must not be null");
        }

        @Test
        @DisplayName("findAndModify(query, source): null source → NPE")
        void findAndModifySource_nullSource_throwsNpe() {
            assertThatThrownBy(() -> repo.findAndModify(new Query(), (Object) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("source must not be null");
        }

        @Test
        @DisplayName("findAndModify(query, source): builds Update and delegates to template")
        void findAndModifySource_buildsUpdateAndDelegates() {
            Query query = new Query();
            PatchPojo source = new PatchPojo("  test  ", "   ", 10);

            when(mongoTemplate.findAndModify(
                    argThat(emptyQuery()),
                    argThat((UpdateDefinition u) -> {
                        Document set = setDoc(u);
                        return "test".equals(set.getString("name"))  // trimmed
                                && Integer.valueOf(10).equals(set.getInteger("age"))
                                && !set.containsKey("blank");          // blank skipped
                    }),
                    argThat(optionsReturnNewTrueUpsertFalse()),
                    eq(PatchPojo.class)
            )).thenReturn(Mono.just(source));

            StepVerifier.create(repo.findAndModify(query, source))
                    .expectNext(source)
                    .verifyComplete();

            verify(mongoTemplate, times(1)).findAndModify(
                    argThat(emptyQuery()),
                    any(UpdateDefinition.class),
                    argThat(optionsReturnNewTrueUpsertFalse()),
                    eq(PatchPojo.class)
            );
        }
    }

    @Nested
    class BuildUpdatePojo {

        @Test
        @DisplayName("buildUpdate(POJO, trimStrings=true): sets non-blank trimmed strings and non-strings; skips null/blank")
        void buildUpdate_pojo_trimTrue() {
            PatchPojo patch = new PatchPojo("  abc  ", "   ", 30);

            Update update = repo.buildUpdate(patch, true);
            Document set = setDoc(update);

            assertThat(set.getString("name")).isEqualTo("abc");
            assertThat(set.getInteger("age")).isEqualTo(30);
            assertThat(set.containsKey("blank")).isFalse();
        }

        @Test
        @DisplayName("buildUpdate(POJO, trimStrings=false): keeps original string (no trim)")
        void buildUpdate_pojo_trimFalse() {
            PatchPojo patch = new PatchPojo("  abc  ", null, 30);

            Update update = repo.buildUpdate(patch, false);
            Document set = setDoc(update);

            assertThat(set.getString("name")).isEqualTo("  abc  ");
            assertThat(set.getInteger("age")).isEqualTo(30);
        }

        @Test
        @DisplayName("buildUpdate(POJO): default trimStrings=true")
        void buildUpdate_pojo_defaultTrimTrue() {
            PatchPojo patch = new PatchPojo("  abc  ", null, null);

            Update update = repo.buildUpdate(patch); // default true
            Document set = setDoc(update);

            assertThat(set.getString("name")).isEqualTo("abc");
        }

        @Test
        @DisplayName("buildUpdate(POJO): getter throws → InternalServerException")
        void buildUpdate_pojo_getterThrows_internalServerException() {
            BadPojo patch = new BadPojo();

            assertThatThrownBy(() -> repo.buildUpdate(patch, true))
                    .isInstanceOf(InternalServerException.class)
                    .hasMessageContaining("Failed to introspect POJO");
        }
    }

    @Nested
    class BuildUpdateRecord {

        @Test
        @DisplayName("buildUpdate(record, trimStrings=true): sets trimmed strings & non-strings; skips blank")
        void buildUpdate_record_trimTrue() {
            PatchRecord patch = new PatchRecord("  xyz  ", "   ", 7);

            Update update = repo.buildUpdate(patch, true);
            Document set = setDoc(update);

            assertThat(set.getString("name")).isEqualTo("xyz");
            assertThat(set.getInteger("age")).isEqualTo(7);
            assertThat(set.containsKey("blank")).isFalse();
        }

        @Test
        @DisplayName("buildUpdate(record, trimStrings=false): keeps original string")
        void buildUpdate_record_trimFalse() {
            PatchRecord patch = new PatchRecord("  xyz  ", null, 7);

            Update update = repo.buildUpdate(patch, false);
            Document set = setDoc(update);

            assertThat(set.getString("name")).isEqualTo("  xyz  ");
            assertThat(set.getInteger("age")).isEqualTo(7);
        }

        @Test
        @DisplayName("buildUpdate(record): accessor throws → InternalServerException (Failed to access record component)")
        void buildUpdate_record_accessorThrows_internalServerException() {
            BadRecord patch = new BadRecord("any");

            assertThatThrownBy(() -> repo.buildUpdate(patch, true))
                    .isInstanceOf(InternalServerException.class)
                    .hasMessageContaining("Failed to access record component: name");
        }
    }

    @Nested
    class BuildUpdateNullChecks {

        @Test
        @DisplayName("buildUpdate(source, trimStrings): null source → NPE")
        void buildUpdate_nullSource_throwsNpe() {
            assertThatThrownBy(() -> repo.buildUpdate(null, true))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("source must not be null");
        }
    }

    @Nested
    class BuildUpdateIncludeNulls {

        @Test
        @DisplayName("buildUpdate(source, trimStrings, includeNulls=true): sets null fields; still skips blank strings")
        void buildUpdate_includeNulls_setsNullFields() {
            PatchPojo patch = new PatchPojo(null, "   ", 30);

            Update update = repo.buildUpdate(patch, true, true);
            Document set = setDoc(update);

            assertThat(set.containsKey("name")).isTrue();
            assertThat(set.get("name")).isNull();
            assertThat(set.containsKey("blank")).isFalse();
            assertThat(set.getInteger("age")).isEqualTo(30);
        }

        @Test
        @DisplayName("buildUpdate(source, trimStrings, includeNulls=true): never nulls id/deviceSerialNumber")
        void buildUpdate_includeNulls_neverNullsIdFields() {
            PatchWithIdRecord patch = new PatchWithIdRecord(null, null, null);

            Update update = repo.buildUpdate(patch, true, true);
            Document set = setDoc(update);

            assertThat(set.containsKey("id")).isFalse();
            assertThat(set.containsKey("deviceSerialNumber")).isFalse();
            assertThat(set.containsKey("name")).isTrue();
            assertThat(set.get("name")).isNull();
        }

        @Test
        @DisplayName("buildUpdate(source, trimStrings, includeNulls=false): behaves like the 2-arg overload")
        void buildUpdate_includeNullsFalse_skipsNulls() {
            PatchPojo patch = new PatchPojo(null, "   ", 30);

            Update update = repo.buildUpdate(patch, true, false);
            Document set = setDoc(update);

            assertThat(set.containsKey("name")).isFalse();
            assertThat(set.getInteger("age")).isEqualTo(30);
        }
    }

    @Test
    @DisplayName("Reflection example: invoke private maybeSet(...) to cover branches explicitly")
    void reflection_invokePrivateMaybeSet() throws Exception {
        Update update = new Update();

        Method maybeSet = BaseReactiveMongoRepositoryImpl.class
                .getDeclaredMethod("maybeSet", Update.class, String.class, Object.class, boolean.class, boolean.class);
        maybeSet.setAccessible(true);
        maybeSet.invoke(repo, update, "a", null, true, false);
        maybeSet.invoke(repo, update, "b", "   ", true, false);
        maybeSet.invoke(repo, update, "c", "  hi  ", true, false);
        maybeSet.invoke(repo, update, "d", 123, true, false);

        Document set = setDoc(update);
        assertThat(set.containsKey("a")).isFalse();
        assertThat(set.containsKey("b")).isFalse();
        assertThat(set.getString("c")).isEqualTo("hi");
        assertThat(set.getInteger("d")).isEqualTo(123);
    }

    @Nested
    class SaveOrUpdateWithExplicitUpdate {

        @Test
        @DisplayName("saveOrUpdate(query, update, clazz): null query → NPE")
        void saveOrUpdate_nullQuery_throwsNpe() {
            assertThatThrownBy(() -> repo.saveOrUpdate(null, new Update().set("x", 1), Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("query must not be null");
        }

        @Test
        @DisplayName("saveOrUpdate(query, update, clazz): null update → NPE")
        void saveOrUpdate_nullUpdate_throwsNpe() {
            assertThatThrownBy(() -> repo.saveOrUpdate(new Query(), null, Object.class))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("update must not be null");
        }

        @Test
        @DisplayName("saveOrUpdate(query, update, clazz): null clazz → NPE")
        void saveOrUpdate_nullClazz_throwsNpe() {
            assertThatThrownBy(() -> repo.saveOrUpdate(new Query(), new Update().set("x", 1), null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("class must not be null");
        }

        @Test
        @DisplayName("saveOrUpdate(query, update, clazz): delegates to template with returnNew=true and upsert=true")
        void saveOrUpdate_delegatesToTemplate_withCorrectOptions() {
            Query query = new Query();
            Update update = new Update().set("name", "upsert-me");
            Object expected = new Object();

            when(mongoTemplate.findAndModify(
                    argThat(emptyQuery()),
                    eq(update),
                    argThat(optionsReturnNewTrueUpsertTrue()),
                    eq(Object.class)
            )).thenReturn(Mono.just(expected));

            StepVerifier.create(repo.saveOrUpdate(query, update, Object.class))
                    .expectNext(expected)
                    .verifyComplete();

            verify(mongoTemplate, times(1)).findAndModify(
                    argThat(emptyQuery()),
                    any(Update.class),
                    argThat(optionsReturnNewTrueUpsertTrue()),
                    eq(Object.class)
            );
            verifyNoMoreInteractions(mongoTemplate);
        }
    }

    @Nested
    class SaveOrUpdateWithSourceObject {

        @Test
        @DisplayName("saveOrUpdate(query, source): null query → NPE")
        void saveOrUpdateSource_nullQuery_throwsNpe() {
            assertThatThrownBy(() -> repo.saveOrUpdate(null, new PatchPojo("name", " ", 9)))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("query must not be null");
        }

        @Test
        @DisplayName("saveOrUpdate(query, source): null source → NPE")
        void saveOrUpdateSource_nullSource_throwsNpe() {
            assertThatThrownBy(() -> repo.saveOrUpdate(new Query(), (Object) null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("source must not be null");
        }

        @Test
        @DisplayName("saveOrUpdate(query, source): builds Update (trimmed) and delegates with upsert=true")
        void saveOrUpdateSource_buildsUpdateAndDelegates_upsertTrue() {
            Query query = new Query();
            PatchPojo source = new PatchPojo("  test  ", "   ", 11);

            when(mongoTemplate.findAndModify(
                    argThat(emptyQuery()),
                    argThat((UpdateDefinition u) -> {
                        Document set = setDoc(u);
                        return "test".equals(set.getString("name"))   // trimmed
                                && Integer.valueOf(11).equals(set.getInteger("age"))
                                && !set.containsKey("blank");          // blank skipped
                    }),
                    argThat(optionsReturnNewTrueUpsertTrue()),
                    eq(PatchPojo.class)
            )).thenReturn(Mono.just(source));

            StepVerifier.create(repo.saveOrUpdate(query, source))
                    .expectNext(source)
                    .verifyComplete();

            verify(mongoTemplate, times(1)).findAndModify(
                    argThat(emptyQuery()),
                    any(UpdateDefinition.class),
                    argThat(optionsReturnNewTrueUpsertTrue()),
                    eq(PatchPojo.class)
            );
            verifyNoMoreInteractions(mongoTemplate);
        }
    }
}
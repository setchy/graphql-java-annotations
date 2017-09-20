/**
 * Copyright 2016 Yurii Rashkovskii
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 */
package graphql.annotations;

import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.schema.*;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static graphql.Scalars.GraphQLString;
import static graphql.schema.GraphQLSchema.newSchema;
import static org.testng.Assert.assertEquals;

public class GraphQLExtensionsTest {

    @GraphQLDescription("TestObject object")
    @GraphQLName("TestObject")
    private static class TestObject {
        @GraphQLField
        public
        String field() {
            return "test";
        }

    }

    @GraphQLTypeExtension(GraphQLExtensionsTest.TestObject.class)
    private static class TestObjectExtension {
        private TestObject obj;

        public TestObjectExtension(TestObject obj) {
            this.obj = obj;
        }

        @GraphQLField
        public String field2() {
            return obj.field() + " test2";
        }

        @GraphQLDataFetcher(TestDataFetcher.class)
        @GraphQLField
        private String field3;
    }

    public static class TestDataFetcher implements DataFetcher {
        @Override
        public Object get(DataFetchingEnvironment environment) {
            return ((TestObject)environment.getSource()).field() + " test3";
        }
    }

    @Test
    public void fields() {
        GraphQLAnnotations.getInstance().registerTypeExtension(TestObjectExtension.class);
        GraphQLObjectType object = GraphQLAnnotations.object(GraphQLExtensionsTest.TestObject.class);
        GraphQLAnnotations.getInstance().unregisterTypeExtension(TestObjectExtension.class);

        List<GraphQLFieldDefinition> fields = object.getFieldDefinitions();
        assertEquals(fields.size(), 3);

        fields.sort((o1, o2) -> o1.getName().compareTo(o2.getName()));

        assertEquals(fields.get(0).getName(), "field");
        assertEquals(fields.get(1).getName(), "field2");
        assertEquals(fields.get(1).getType(), GraphQLString);
        assertEquals(fields.get(2).getName(), "field3");
        assertEquals(fields.get(2).getType(), GraphQLString);
    }

    @Test
    public void values() {
        GraphQLAnnotations.getInstance().registerTypeExtension(TestObjectExtension.class);
        GraphQLObjectType object = GraphQLAnnotations.object(GraphQLExtensionsTest.TestObject.class);
        GraphQLAnnotations.getInstance().unregisterTypeExtension(TestObjectExtension.class);

        GraphQLSchema schema = newSchema().query(object).build();
        GraphQLSchema schemaInherited = newSchema().query(object).build();

        ExecutionResult result = GraphQL.newGraphQL(schema).build().execute("{field}", new GraphQLExtensionsTest.TestObject());
        assertEquals(((Map<String, Object>) result.getData()).get("field"), "test");
        ExecutionResult result2 = GraphQL.newGraphQL(schema).build().execute("{field2}", new GraphQLExtensionsTest.TestObject());
        assertEquals(((Map<String, Object>) result2.getData()).get("field2"), "test test2");
        ExecutionResult result3 = GraphQL.newGraphQL(schema).build().execute("{field3}", new GraphQLExtensionsTest.TestObject());
        assertEquals(((Map<String, Object>) result3.getData()).get("field3"), "test test3");
    }

}

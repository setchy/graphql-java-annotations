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
package graphql.annotations.processor;

import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.directives.definition.GraphQLDirectiveDefinition;
import graphql.annotations.processor.directives.CommonPropertiesCreator;
import graphql.annotations.processor.directives.DirectiveArgumentCreator;
import graphql.annotations.processor.directives.DirectiveCreator;
import graphql.annotations.processor.exceptions.GraphQLAnnotationsException;
import graphql.annotations.processor.graphQLProcessors.GraphQLAnnotationsProcessor;
import graphql.annotations.processor.graphQLProcessors.GraphQLInputProcessor;
import graphql.annotations.processor.graphQLProcessors.GraphQLOutputProcessor;
import graphql.annotations.processor.retrievers.*;
import graphql.annotations.processor.searchAlgorithms.BreadthFirstSearch;
import graphql.annotations.processor.searchAlgorithms.ParentalSearch;
import graphql.annotations.processor.typeFunctions.DefaultTypeFunction;
import graphql.annotations.processor.typeFunctions.TypeFunction;
import graphql.annotations.processor.util.DataFetcherConstructor;
import graphql.relay.Relay;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInterfaceType;
import graphql.schema.GraphQLObjectType;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static graphql.annotations.processor.util.NamingKit.toGraphqlName;

/**
 * A utility class for extracting GraphQL data structures from annotated
 * elements.
 */
public class GraphQLAnnotations implements GraphQLAnnotationsProcessor {

    public static final String NOT_PROPERLY_ANNOTATION_ERROR = "The supplied class %s is not annotated with a GraphQLDirectiveDefinition annotation";
    private GraphQLObjectHandler graphQLObjectHandler;
    private GraphQLExtensionsHandler graphQLExtensionsHandler;
    private DirectiveCreator directiveCreator;

    private ProcessingElementsContainer container;

    public GraphQLAnnotations() {
        GraphQLObjectHandler objectHandler = new GraphQLObjectHandler();
        GraphQLTypeRetriever typeRetriever = new GraphQLTypeRetriever();
        GraphQLObjectInfoRetriever objectInfoRetriever = new GraphQLObjectInfoRetriever();
        GraphQLInterfaceRetriever interfaceRetriever = new GraphQLInterfaceRetriever();
        GraphQLFieldRetriever fieldRetriever = new GraphQLFieldRetriever();
        GraphQLInputProcessor inputProcessor = new GraphQLInputProcessor();
        GraphQLOutputProcessor outputProcessor = new GraphQLOutputProcessor();
        BreadthFirstSearch methodSearchAlgorithm = new BreadthFirstSearch(objectInfoRetriever);
        ParentalSearch fieldSearchAlgorithm = new ParentalSearch(objectInfoRetriever);
        DataFetcherConstructor dataFetcherConstructor = new DataFetcherConstructor();
        GraphQLExtensionsHandler extensionsHandler = new GraphQLExtensionsHandler();
        DefaultTypeFunction defaultTypeFunction = new DefaultTypeFunction(inputProcessor, outputProcessor);

        objectHandler.setTypeRetriever(typeRetriever);
        typeRetriever.setGraphQLObjectInfoRetriever(objectInfoRetriever);
        typeRetriever.setGraphQLInterfaceRetriever(interfaceRetriever);
        typeRetriever.setMethodSearchAlgorithm(methodSearchAlgorithm);
        typeRetriever.setFieldSearchAlgorithm(fieldSearchAlgorithm);
        typeRetriever.setExtensionsHandler(extensionsHandler);
        typeRetriever.setGraphQLFieldRetriever(fieldRetriever);
        interfaceRetriever.setGraphQLTypeRetriever(typeRetriever);
        fieldRetriever.setDataFetcherConstructor(dataFetcherConstructor);
        inputProcessor.setGraphQLTypeRetriever(typeRetriever);
        outputProcessor.setGraphQLTypeRetriever(typeRetriever);
        extensionsHandler.setGraphQLObjectInfoRetriever(objectInfoRetriever);
        extensionsHandler.setFieldSearchAlgorithm(fieldSearchAlgorithm);
        extensionsHandler.setMethodSearchAlgorithm(methodSearchAlgorithm);
        extensionsHandler.setFieldRetriever(fieldRetriever);

        this.graphQLObjectHandler = objectHandler;
        this.graphQLExtensionsHandler = extensionsHandler;
        this.container = new ProcessingElementsContainer(defaultTypeFunction);

        DirectiveArgumentCreator directiveArgumentCreator = new DirectiveArgumentCreator(new CommonPropertiesCreator(),
                container.getDefaultTypeFunction(), container);
        this.directiveCreator = new DirectiveCreator(directiveArgumentCreator, new CommonPropertiesCreator());
    }

    public GraphQLAnnotations(TypeFunction defaultTypeFunction, GraphQLObjectHandler graphQLObjectHandler, GraphQLExtensionsHandler graphQLExtensionsHandler) {
        this.graphQLObjectHandler = graphQLObjectHandler;
        this.graphQLExtensionsHandler = graphQLExtensionsHandler;
        this.container = new ProcessingElementsContainer(defaultTypeFunction);
    }

    public void setRelay(Relay relay) {
        this.container.setRelay(relay);
    }

    public String getTypeName(Class<?> objectClass) {
        GraphQLName name = objectClass.getAnnotation(GraphQLName.class);
        return toGraphqlName(name == null ? objectClass.getSimpleName() : name.value());
    }

    public GraphQLInterfaceType generateInterface(Class<?> object) throws GraphQLAnnotationsException {
        try {
            return this.graphQLObjectHandler.getGraphQLType(object, this.getContainer());
        } catch (GraphQLAnnotationsException e) {
            this.getContainer().getProcessing().clear();
            this.getTypeRegistry().clear();
            throw e;
        }
    }

    public GraphQLObjectType object(Class<?> object) throws GraphQLAnnotationsException {
        try {
            return this.graphQLObjectHandler.getGraphQLType(object, this.getContainer());
        } catch (GraphQLAnnotationsException e) {
            this.getContainer().getProcessing().clear();
            this.getTypeRegistry().clear();
            throw e;
        }
    }

    public GraphQLDirective directive(Class<?> object) throws GraphQLAnnotationsException {
        if (!object.isAnnotationPresent(GraphQLDirectiveDefinition.class)){
            throw new GraphQLAnnotationsException(String.format(NOT_PROPERLY_ANNOTATION_ERROR, object.getSimpleName()), null);
        }

        try {
            GraphQLDirective directive = this.directiveCreator.getDirective(object);
            GraphQLDirectiveDefinition annotation = object.getAnnotation(GraphQLDirectiveDefinition.class);
            this.getContainer().getDirectiveRegistry().put(directive.getName(), new DirectiveAndWiring(directive, annotation.wiring()));
            return directive;
        } catch (GraphQLAnnotationsException e) {
            this.getContainer().getProcessing().clear();
            this.getTypeRegistry().clear();
            throw e;
        }
    }

    public Set<GraphQLDirective> directives(Class<?> directivesDeclarationClass) {
        Method[] methods = directivesDeclarationClass.getMethods();
        Set<GraphQLDirective> directiveSet = new HashSet<>();
        Arrays.stream(methods).sequential().filter(method -> method.isAnnotationPresent(GraphQLDirectiveDefinition.class))
                .forEach(method -> {
                    try {
                        DirectiveAndWiring directiveAndWiring = this.directiveCreator.getDirectiveAndWiring(method);
                        this.getContainer().getDirectiveRegistry().put(directiveAndWiring.getDirective().getName(), directiveAndWiring);
                        directiveSet.add(directiveAndWiring.getDirective());
                    } catch (GraphQLAnnotationsException e) {
                        this.getContainer().getProcessing().clear();
                        this.getTypeRegistry().clear();
                        throw e;
                    }
                });
        return directiveSet;
    }

    public void registerTypeExtension(Class<?> objectClass) {
        graphQLExtensionsHandler.registerTypeExtension(objectClass, container);
    }

    public void registerTypeFunction(TypeFunction typeFunction) {
        ((DefaultTypeFunction) container.getDefaultTypeFunction()).register(typeFunction);
    }


    public Map<String, graphql.schema.GraphQLType> getTypeRegistry() {
        return container.getTypeRegistry();
    }

    public GraphQLObjectHandler getObjectHandler() {
        return graphQLObjectHandler;
    }

    public GraphQLExtensionsHandler getExtensionsHandler() {
        return graphQLExtensionsHandler;
    }

    public ProcessingElementsContainer getContainer() {
        return container;
    }

    public void setContainer(ProcessingElementsContainer container) {
        this.container = container;
    }

    public void setDefaultTypeFunction(TypeFunction function) {
        this.container.setDefaultTypeFunction(function);
    }

}

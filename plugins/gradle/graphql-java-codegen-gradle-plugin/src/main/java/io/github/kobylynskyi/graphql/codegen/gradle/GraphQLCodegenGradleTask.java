package io.github.kobylynskyi.graphql.codegen.gradle;

import com.kobylynskyi.graphql.codegen.GraphQLCodegen;
import com.kobylynskyi.graphql.codegen.java.JavaGraphQLCodegen;
import com.kobylynskyi.graphql.codegen.kotlin.KotlinGraphQLCodegen;
import com.kobylynskyi.graphql.codegen.model.ApiInterfaceStrategy;
import com.kobylynskyi.graphql.codegen.model.ApiNamePrefixStrategy;
import com.kobylynskyi.graphql.codegen.model.ApiRootInterfaceStrategy;
import com.kobylynskyi.graphql.codegen.model.GeneratedLanguage;
import com.kobylynskyi.graphql.codegen.model.GraphQLCodegenConfiguration;
import com.kobylynskyi.graphql.codegen.model.MappingConfig;
import com.kobylynskyi.graphql.codegen.model.MappingConfigConstants;
import com.kobylynskyi.graphql.codegen.model.exception.LanguageNotSupportedException;
import com.kobylynskyi.graphql.codegen.scala.ScalaGraphQLCodegen;
import com.kobylynskyi.graphql.codegen.supplier.MappingConfigSupplier;
import com.kobylynskyi.graphql.codegen.supplier.MergeableMappingConfigSupplier;
import com.kobylynskyi.graphql.codegen.supplier.SchemaFinder;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Gradle task for GraphQL code generation
 *
 * @author kobylynskyi
 */
public class GraphQLCodegenGradleTask extends DefaultTask implements GraphQLCodegenConfiguration {

    private List<String> graphqlSchemaPaths;
    private String graphqlQueryIntrospectionResultPath;
    private final SchemaFinderConfig graphqlSchemas = new SchemaFinderConfig();
    private File outputDir;

    private Map<String, String> customTypesMapping = new HashMap<>();
    private Map<String, List<String>> customAnnotationsMapping = new HashMap<>();
    private Map<String, String> customTemplates = new HashMap<>();
    private Map<String, List<String>> directiveAnnotationsMapping = new HashMap<>();
    private String packageName;
    private String apiPackageName;
    private ApiNamePrefixStrategy apiNamePrefixStrategy = MappingConfigConstants.DEFAULT_API_NAME_PREFIX_STRATEGY;
    private ApiRootInterfaceStrategy apiRootInterfaceStrategy = MappingConfigConstants.DEFAULT_API_ROOT_INTERFACE_STRATEGY;
    private ApiInterfaceStrategy apiInterfaceStrategy = MappingConfigConstants.DEFAULT_API_INTERFACE_STRATEGY;
    private String apiNamePrefix;
    private String apiNameSuffix = MappingConfigConstants.DEFAULT_RESOLVER_SUFFIX;
    private String typeResolverPrefix;
    private String typeResolverSuffix = MappingConfigConstants.DEFAULT_RESOLVER_SUFFIX;
    private String modelPackageName;
    private String modelNamePrefix;
    private String modelNameSuffix;
    private String apiReturnType;
    private String apiReturnListType;
    private String subscriptionReturnType;
    private Boolean generateBuilder = MappingConfigConstants.DEFAULT_BUILDER;
    private Boolean generateApis = MappingConfigConstants.DEFAULT_GENERATE_APIS;
    private String modelValidationAnnotation;
    private Boolean generateEqualsAndHashCode = MappingConfigConstants.DEFAULT_EQUALS_AND_HASHCODE;
    private Boolean generateImmutableModels = MappingConfigConstants.DEFAULT_GENERATE_IMMUTABLE_MODELS;
    private Boolean generateToString = MappingConfigConstants.DEFAULT_TO_STRING;
    private Boolean generateParameterizedFieldsResolvers = MappingConfigConstants.DEFAULT_GENERATE_PARAMETERIZED_FIELDS_RESOLVERS;
    private Boolean generateExtensionFieldsResolvers = MappingConfigConstants.DEFAULT_GENERATE_EXTENSION_FIELDS_RESOLVERS;
    private Boolean generateDataFetchingEnvironmentArgumentInApis = MappingConfigConstants.DEFAULT_GENERATE_DATA_FETCHING_ENV;
    private Boolean generateModelsForRootTypes = MappingConfigConstants.DEFAULT_GENERATE_MODELS_FOR_ROOT_TYPES;
    private Boolean useOptionalForNullableReturnTypes = MappingConfigConstants.DEFAULT_USE_OPTIONAL_FOR_NULLABLE_RETURN_TYPES;
    private Boolean generateApisWithThrowsException = MappingConfigConstants.DEFAULT_GENERATE_APIS_WITH_THROWS_EXCEPTION;
    private Boolean generateJacksonTypeIdResolver = MappingConfigConstants.DEFAULT_GENERATE_JACKSON_TYPE_ID_RESOLVER;
    private Boolean addGeneratedAnnotation = MappingConfigConstants.DEFAULT_ADD_GENERATED_ANNOTATION;
    private Boolean generateNoArgsConstructorOnly = MappingConfigConstants.DEFAULT_GENERATE_NOARGS_CONSTRUCTOR_ONLY;
    private Boolean generateModelsWithPublicFields = MappingConfigConstants.DEFAULT_GENERATE_MODELS_WITH_PUBLIC_FIELDS;
    private String generatedAnnotation;
    private Set<String> fieldsWithResolvers = new HashSet<>();
    private Set<String> fieldsWithoutResolvers = new HashSet<>();
    private Set<String> typesAsInterfaces = new HashSet<>();
    private Set<String> resolverArgumentAnnotations = new HashSet<>();
    private Set<String> parametrizedResolverAnnotations = new HashSet<>();
    private final RelayConfig relayConfig = new RelayConfig();


    private Boolean generateClient;
    private String requestSuffix;
    private String responseSuffix;
    private String responseProjectionSuffix;
    private String parametrizedInputSuffix;
    private Boolean generateAllMethodInProjection = MappingConfigConstants.DEFAULT_GENERATE_ALL_METHOD;
    private int responseProjectionMaxDepth = MappingConfigConstants.DEFAULT_RESPONSE_PROJECTION_MAX_DEPTH;
    private Set<String> useObjectMapperForRequestSerialization = new HashSet<>();

    private final ParentInterfacesConfig parentInterfaces = new ParentInterfacesConfig();
    private List<String> configurationFiles;
    private GeneratedLanguage generatedLanguage = MappingConfigConstants.DEFAULT_GENERATED_LANGUAGE;
    private Boolean generateModelOpenClasses = MappingConfigConstants.DEFAULT_GENERATE_MODEL_OPEN_CLASSES;
    private Boolean initializeNullableTypes = MappingConfigConstants.DEFAULT_INITIALIZE_NULLABLE_TYPES;
    private Boolean generateSealedInterfaces = MappingConfigConstants.DEFAULT_GENERATE_SEALED_INTERFACES;

    private Boolean supportUnknownFields = MappingConfigConstants.DEFAULT_SUPPORT_UNKNOWN_FIELDS;
    private String unknownFieldsPropertyName = MappingConfigConstants.DEFAULT_UNKNOWN_FIELDS_PROPERTY_NAME;

    private Boolean skip = false;

    public GraphQLCodegenGradleTask() {
        setGroup("codegen");
        setDescription("Generates Java POJOs and interfaces based on GraphQL schemas");
    }

    /**
     * Perform code generation
     *
     * @throws Exception in case source file is not found, there is an invalid schema or there are any problems
     *                   with a configuration
     */
    @TaskAction
    public void generate() throws Exception {
        MappingConfig mappingConfig = new MappingConfig();
        mappingConfig.setPackageName(packageName);
        mappingConfig.setCustomTypesMapping(
                customTypesMapping != null ? customTypesMapping : new HashMap<>());
        mappingConfig.setCustomTemplates(
                customTemplates != null ? customTemplates : new HashMap<>());
        mappingConfig.setDirectiveAnnotationsMapping(
                directiveAnnotationsMapping != null ? directiveAnnotationsMapping : new HashMap<>());
        mappingConfig.setApiNameSuffix(apiNameSuffix);
        mappingConfig.setApiNamePrefix(apiNamePrefix);
        mappingConfig.setApiRootInterfaceStrategy(apiRootInterfaceStrategy);
        mappingConfig.setApiInterfaceStrategy(apiInterfaceStrategy);
        mappingConfig.setApiNamePrefixStrategy(apiNamePrefixStrategy);
        mappingConfig.setModelNamePrefix(modelNamePrefix);
        mappingConfig.setModelNameSuffix(modelNameSuffix);
        mappingConfig.setApiPackageName(apiPackageName);
        mappingConfig.setModelPackageName(modelPackageName);
        mappingConfig.setGenerateBuilder(generateBuilder);
        mappingConfig.setGenerateApis(generateApis);
        mappingConfig.setTypeResolverSuffix(typeResolverSuffix);
        mappingConfig.setTypeResolverPrefix(typeResolverPrefix);
        mappingConfig.setModelValidationAnnotation(modelValidationAnnotation);
        mappingConfig.setGenerateEqualsAndHashCode(generateEqualsAndHashCode);
        mappingConfig.setGenerateImmutableModels(generateImmutableModels);
        mappingConfig.setGenerateToString(generateToString);
        mappingConfig.setUseOptionalForNullableReturnTypes(useOptionalForNullableReturnTypes);
        mappingConfig.setGenerateApisWithThrowsException(generateApisWithThrowsException);
        mappingConfig.setGenerateJacksonTypeIdResolver(generateJacksonTypeIdResolver);
        mappingConfig.setGenerateNoArgsConstructorOnly(generateNoArgsConstructorOnly);
        mappingConfig.setGenerateModelsWithPublicFields(generateModelsWithPublicFields);
        mappingConfig.setAddGeneratedAnnotation(addGeneratedAnnotation);
        mappingConfig.setGeneratedAnnotation(generatedAnnotation);
        mappingConfig.setApiReturnType(apiReturnType);
        mappingConfig.setApiReturnListType(apiReturnListType);
        mappingConfig.setSubscriptionReturnType(subscriptionReturnType);
        mappingConfig.setGenerateParameterizedFieldsResolvers(generateParameterizedFieldsResolvers);
        mappingConfig.setGenerateDataFetchingEnvironmentArgumentInApis(generateDataFetchingEnvironmentArgumentInApis);
        mappingConfig.setGenerateExtensionFieldsResolvers(generateExtensionFieldsResolvers);
        mappingConfig.setGenerateModelsForRootTypes(generateModelsForRootTypes);
        mappingConfig.setFieldsWithResolvers(
                fieldsWithResolvers != null ? fieldsWithResolvers : new HashSet<>());
        mappingConfig.setFieldsWithoutResolvers(
                fieldsWithoutResolvers != null ? fieldsWithoutResolvers : new HashSet<>());
        mappingConfig.setTypesAsInterfaces(
                typesAsInterfaces != null ? typesAsInterfaces : new HashSet<>());
        mappingConfig.setResolverArgumentAnnotations(
                resolverArgumentAnnotations != null ? resolverArgumentAnnotations : new HashSet<>());
        mappingConfig.setParametrizedResolverAnnotations(
                parametrizedResolverAnnotations != null ? parametrizedResolverAnnotations : new HashSet<>());
        mappingConfig.setRelayConfig(relayConfig);

        mappingConfig.setGenerateClient(generateClient);
        mappingConfig.setRequestSuffix(requestSuffix);
        mappingConfig.setResponseSuffix(responseSuffix);
        mappingConfig.setResponseProjectionSuffix(responseProjectionSuffix);
        mappingConfig.setParametrizedInputSuffix(parametrizedInputSuffix);
        mappingConfig.setUseObjectMapperForRequestSerialization(useObjectMapperForRequestSerialization != null ?
                useObjectMapperForRequestSerialization : new HashSet<>());
        mappingConfig.setGenerateAllMethodInProjection(generateAllMethodInProjection);
        mappingConfig.setResponseProjectionMaxDepth(responseProjectionMaxDepth);

        mappingConfig.setResolverParentInterface(getResolverParentInterface());
        mappingConfig.setQueryResolverParentInterface(getQueryResolverParentInterface());
        mappingConfig.setMutationResolverParentInterface(getMutationResolverParentInterface());
        mappingConfig.setSubscriptionResolverParentInterface(getSubscriptionResolverParentInterface());

        mappingConfig.setGeneratedLanguage(generatedLanguage);
        mappingConfig.setGenerateModelOpenClasses(generateModelOpenClasses);
        mappingConfig.setInitializeNullableTypes(initializeNullableTypes);

        mappingConfig.setSupportUnknownFields(isSupportUnknownFields());
        mappingConfig.setUnknownFieldsPropertyName(getUnknownFieldsPropertyName());

        if (Boolean.TRUE.equals(skip)) {
            getLogger().info("Skipping code generation");
            return;
        }

        instantiateCodegen(mappingConfig).generate();
    }

    private GraphQLCodegen instantiateCodegen(MappingConfig mappingConfig) throws IOException {
        java.util.Optional<MappingConfigSupplier> mappingConfigSupplier = buildJsonSupplier();
        GeneratedLanguage language = mappingConfigSupplier.map(Supplier::get)
                .map(MappingConfig::getGeneratedLanguage)
                .orElse(generatedLanguage);
        switch (language) {
            case JAVA:
                return new JavaGraphQLCodegen(getActualSchemaPaths(), graphqlQueryIntrospectionResultPath,
                        outputDir, mappingConfig, mappingConfigSupplier.orElse(null));
            case SCALA:
                return new ScalaGraphQLCodegen(getActualSchemaPaths(), graphqlQueryIntrospectionResultPath,
                        outputDir, mappingConfig, mappingConfigSupplier.orElse(null));
            case KOTLIN:
                return new KotlinGraphQLCodegen(getActualSchemaPaths(), graphqlQueryIntrospectionResultPath,
                        outputDir, mappingConfig, mappingConfigSupplier.orElse(null));
            default:
                throw new LanguageNotSupportedException(language);
        }
    }

    /**
     * This is only public so that it can be part of the inputs.
     * Changes to schema contents need to invalidate this task, and require re-run.
     * Using the SchemaFinder, we need to calculate the resulting list of paths as @InputFiles for it to work.
     *
     * @return a list of schema files that will be processed.
     * @throws IOException in case some I/O error occurred
     */
    @InputFiles
    public List<String> getActualSchemaPaths() throws IOException {
        if (graphqlSchemaPaths != null) {
            return graphqlSchemaPaths;
        }
        if (graphqlQueryIntrospectionResultPath != null) {
            return Collections.emptyList();
        }
        Path rootDir = getSchemasRootDir();
        SchemaFinder finder = new SchemaFinder(rootDir);
        finder.setRecursive(graphqlSchemas.isRecursive());
        finder.setIncludePattern(graphqlSchemas.getIncludePattern());
        finder.setExcludedFiles(graphqlSchemas.getExcludedFiles());
        return finder.findSchemas();
    }

    private Path getSchemasRootDir() {
        String rootDir = graphqlSchemas.getRootDir();
        if (rootDir == null) {
            return findDefaultResourcesDir().orElseThrow(() -> new IllegalStateException(
                    "Default resource folder not found, please provide graphqlSchemas.rootDir"));
        }
        return Paths.get(rootDir);
    }

    private java.util.Optional<Path> findDefaultResourcesDir() {
        return getProject().getConvention()
                .getPlugin(JavaPluginConvention.class)
                .getSourceSets()
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                .getResources()
                .getSourceDirectories()
                .getFiles()
                .stream()
                .findFirst()
                .map(File::toPath);
    }

    private java.util.Optional<MappingConfigSupplier> buildJsonSupplier() {
        if (configurationFiles != null && !configurationFiles.isEmpty()) {
            return java.util.Optional.of(new MergeableMappingConfigSupplier(configurationFiles));
        }
        return java.util.Optional.empty();
    }

    @InputFiles
    @Optional
    public List<String> getGraphqlSchemaPaths() {
        return graphqlSchemaPaths;
    }

    public void setGraphqlSchemaPaths(List<String> graphqlSchemaPaths) {
        this.graphqlSchemaPaths = graphqlSchemaPaths;
    }

    @InputFile
    @Optional
    public String getGraphqlQueryIntrospectionResultPath() {
        return graphqlQueryIntrospectionResultPath;
    }

    public void setGraphqlQueryIntrospectionResultPath(String graphqlQueryIntrospectionResultPath) {
        this.graphqlQueryIntrospectionResultPath = graphqlQueryIntrospectionResultPath;
    }

    @Nested
    @Optional
    public SchemaFinderConfig getGraphqlSchemas() {
        return graphqlSchemas;
    }

    public void graphqlSchemas(Action<? super SchemaFinderConfig> action) {
        action.execute(graphqlSchemas);
    }

    @OutputDirectory
    public File getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(File outputDir) {
        this.outputDir = outputDir;
    }

    @Input
    @Optional
    @Override
    public Map<String, String> getCustomTypesMapping() {
        return customTypesMapping;
    }

    public void setCustomTypesMapping(Map<String, String> customTypesMapping) {
        this.customTypesMapping = customTypesMapping;
    }

    @Input
    @Optional
    @Override
    public Map<String, String> getCustomTemplates() {
        return customTemplates;
    }

    public void setCustomTemplates(Map<String, String> customTemplates) {
        this.customTemplates = customTemplates;
    }

    @Input
    @Optional
    @Override
    public Map<String, List<String>> getCustomAnnotationsMapping() {
        return customAnnotationsMapping;
    }

    public void setCustomAnnotationsMapping(Map<String, List<String>> customAnnotationsMapping) {
        this.customAnnotationsMapping = customAnnotationsMapping;
    }

    @Input
    @Optional
    @Override
    public Map<String, List<String>> getDirectiveAnnotationsMapping() {
        return directiveAnnotationsMapping;
    }

    public void setDirectiveAnnotationsMapping(Map<String, List<String>> directiveAnnotationsMapping) {
        this.directiveAnnotationsMapping = directiveAnnotationsMapping;
    }

    @Input
    @Optional
    @Override
    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    @Input
    @Optional
    @Override
    public String getModelNamePrefix() {
        return modelNamePrefix;
    }

    public void setModelNameSuffix(String modelNameSuffix) {
        this.modelNameSuffix = modelNameSuffix;
    }

    @Input
    @Optional
    @Override
    public String getModelNameSuffix() {
        return modelNameSuffix;
    }

    public void setModelNamePrefix(String modelNamePrefix) {
        this.modelNamePrefix = modelNamePrefix;
    }

    @Input
    @Optional
    @Override
    public String getApiPackageName() {
        return apiPackageName;
    }

    public void setApiPackageName(String apiPackageName) {
        this.apiPackageName = apiPackageName;
    }

    @Input
    @Optional
    @Override
    public ApiRootInterfaceStrategy getApiRootInterfaceStrategy() {
        return apiRootInterfaceStrategy;
    }

    public void setApiRootInterfaceStrategy(ApiRootInterfaceStrategy apiRootInterfaceStrategy) {
        this.apiRootInterfaceStrategy = apiRootInterfaceStrategy;
    }

    @Input
    @Optional
    @Override
    public ApiInterfaceStrategy getApiInterfaceStrategy() {
        return apiInterfaceStrategy;
    }

    public void setApiInterfaceStrategy(ApiInterfaceStrategy apiInterfaceStrategy) {
        this.apiInterfaceStrategy = apiInterfaceStrategy;
    }

    @Input
    @Optional
    @Override
    public ApiNamePrefixStrategy getApiNamePrefixStrategy() {
        return apiNamePrefixStrategy;
    }

    public void setApiNamePrefixStrategy(ApiNamePrefixStrategy apiNamePrefixStrategy) {
        this.apiNamePrefixStrategy = apiNamePrefixStrategy;
    }

    @Input
    @Optional
    @Override
    public String getApiNamePrefix() {
        return apiNamePrefix;
    }

    public void setApiNamePrefix(String apiNamePrefix) {
        this.apiNamePrefix = apiNamePrefix;
    }

    @Input
    @Optional
    @Override
    public String getApiNameSuffix() {
        return apiNameSuffix;
    }

    public void setApiNameSuffix(String apiNameSuffix) {
        this.apiNameSuffix = apiNameSuffix;
    }

    @Input
    @Optional
    @Override
    public String getModelPackageName() {
        return modelPackageName;
    }

    public void setModelPackageName(String modelPackageName) {
        this.modelPackageName = modelPackageName;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateBuilder() {
        return generateBuilder;
    }

    public void setGenerateBuilder(Boolean generateBuilder) {
        this.generateBuilder = generateBuilder;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateApis() {
        return generateApis;
    }

    public void setGenerateApis(Boolean generateApis) {
        this.generateApis = generateApis;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateModelsForRootTypes() {
        return generateModelsForRootTypes;
    }

    public void setGenerateModelsForRootTypes(Boolean generateModelsForRootTypes) {
        this.generateModelsForRootTypes = generateModelsForRootTypes;
    }

    @Input
    @Optional
    @Override
    public String getModelValidationAnnotation() {
        return modelValidationAnnotation;
    }

    public void setModelValidationAnnotation(String modelValidationAnnotation) {
        this.modelValidationAnnotation = modelValidationAnnotation;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateEqualsAndHashCode() {
        return generateEqualsAndHashCode;
    }

    public void setGenerateEqualsAndHashCode(Boolean generateEqualsAndHashCode) {
        this.generateEqualsAndHashCode = generateEqualsAndHashCode;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateImmutableModels() {
        return generateImmutableModels;
    }

    public void setGenerateImmutableModels(Boolean generateImmutableModels) {
        this.generateImmutableModels = generateImmutableModels;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateToString() {
        return generateToString;
    }

    public void setGenerateToString(Boolean generateToString) {
        this.generateToString = generateToString;
    }

    @Input
    @Optional
    @Override
    public String getApiReturnType() {
        return apiReturnType;
    }

    public void setApiReturnType(String apiReturnType) {
        this.apiReturnType = apiReturnType;
    }

    @Input
    @Optional
    @Override
    public String getApiReturnListType() {
        return apiReturnListType;
    }

    public void setApiReturnListType(String apiReturnListType) {
        this.apiReturnListType = apiReturnListType;
    }

    @Input
    @Optional
    @Override
    public String getSubscriptionReturnType() {
        return subscriptionReturnType;
    }

    public void setSubscriptionReturnType(String subscriptionReturnType) {
        this.subscriptionReturnType = subscriptionReturnType;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateParameterizedFieldsResolvers() {
        return generateParameterizedFieldsResolvers;
    }

    public void setGenerateParameterizedFieldsResolvers(Boolean generateParameterizedFieldsResolvers) {
        this.generateParameterizedFieldsResolvers = generateParameterizedFieldsResolvers;
    }

    @Input
    @Optional
    @Override
    public String getTypeResolverPrefix() {
        return typeResolverPrefix;
    }

    public void setTypeResolverPrefix(String typeResolverPrefix) {
        this.typeResolverPrefix = typeResolverPrefix;
    }

    @Input
    @Optional
    @Override
    public String getTypeResolverSuffix() {
        return typeResolverSuffix;
    }

    public void setTypeResolverSuffix(String typeResolverSuffix) {
        this.typeResolverSuffix = typeResolverSuffix;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateExtensionFieldsResolvers() {
        return generateExtensionFieldsResolvers;
    }

    public void setGenerateExtensionFieldsResolvers(Boolean generateExtensionFieldsResolvers) {
        this.generateExtensionFieldsResolvers = generateExtensionFieldsResolvers;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateDataFetchingEnvironmentArgumentInApis() {
        return generateDataFetchingEnvironmentArgumentInApis;
    }

    public void setGenerateDataFetchingEnvironmentArgumentInApis(
            Boolean generateDataFetchingEnvironmentArgumentInApis) {
        this.generateDataFetchingEnvironmentArgumentInApis = generateDataFetchingEnvironmentArgumentInApis;
    }

    @Input
    @Optional
    @Override
    public Boolean getUseOptionalForNullableReturnTypes() {
        return useOptionalForNullableReturnTypes;
    }

    public void setUseOptionalForNullableReturnTypes(Boolean useOptionalForNullableReturnTypes) {
        this.useOptionalForNullableReturnTypes = useOptionalForNullableReturnTypes;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateApisWithThrowsException() {
        return generateApisWithThrowsException;
    }

    public void setGenerateApisWithThrowsException(Boolean generateApisWithThrowsException) {
        this.generateApisWithThrowsException = generateApisWithThrowsException;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateJacksonTypeIdResolver() {
        return generateJacksonTypeIdResolver;
    }

    public void setGenerateJacksonTypeIdResolver(Boolean generateJacksonTypeIdResolver) {
        this.generateJacksonTypeIdResolver = generateJacksonTypeIdResolver;
    }

    @Input
    @Optional
    @Override
    public Boolean getAddGeneratedAnnotation() {
        return addGeneratedAnnotation;
    }

    public void setAddGeneratedAnnotation(Boolean addGeneratedAnnotation) {
        this.addGeneratedAnnotation = addGeneratedAnnotation;
    }

    @Input
    @Optional
    @Override
    public String getGeneratedAnnotation() {
        return generatedAnnotation;
    }

    public void setGeneratedAnnotation(String generatedAnnotation) {
        this.generatedAnnotation = generatedAnnotation;
    }

    @Input
    @Optional
    @Override
    public Boolean isGenerateNoArgsConstructorOnly() {
        return generateNoArgsConstructorOnly;
    }

    public void setGenerateNoArgsConstructorOnly(Boolean generateNoArgsConstructorOnly) {
        this.generateNoArgsConstructorOnly = generateNoArgsConstructorOnly;
    }

    @Input
    @Optional
    @Override
    public Boolean isGenerateModelsWithPublicFields() {
        return generateModelsWithPublicFields;
    }

    public void setGenerateModelsWithPublicFields(Boolean generateModelsWithPublicFields) {
        this.generateModelsWithPublicFields = generateModelsWithPublicFields;
    }

    @Input
    @Optional
    @Override
    public Set<String> getFieldsWithResolvers() {
        return fieldsWithResolvers;
    }

    public void setFieldsWithResolvers(Set<String> fieldsWithResolvers) {
        this.fieldsWithResolvers = fieldsWithResolvers;
    }

    @Input
    @Optional
    @Override
    public Set<String> getFieldsWithoutResolvers() {
        return fieldsWithoutResolvers;
    }

    public void setFieldsWithoutResolvers(Set<String> fieldsWithoutResolvers) {
        this.fieldsWithoutResolvers = fieldsWithoutResolvers;
    }

    @Input
    @Optional
    @Override
    public Set<String> getTypesAsInterfaces() {
        return typesAsInterfaces;
    }

    public void setTypesAsInterfaces(Set<String> typesAsInterfaces) {
        this.typesAsInterfaces = typesAsInterfaces;
    }

    @Input
    @Optional
    @Override
    public Set<String> getResolverArgumentAnnotations() {
        return resolverArgumentAnnotations;
    }

    public void setResolverArgumentAnnotations(Set<String> resolverArgumentAnnotations) {
        this.resolverArgumentAnnotations = resolverArgumentAnnotations;
    }

    @Input
    @Optional
    @Override
    public Set<String> getParametrizedResolverAnnotations() {
        return parametrizedResolverAnnotations;
    }

    public void setParametrizedResolverAnnotations(Set<String> parametrizedResolverAnnotations) {
        this.parametrizedResolverAnnotations = parametrizedResolverAnnotations;
    }

    @Nested
    @Optional
    @Override
    public RelayConfig getRelayConfig() {
        return relayConfig;
    }

    public void relayConfig(Action<? super RelayConfig> action) {
        action.execute(relayConfig);
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateClient() {
        return generateClient;
    }

    public void setGenerateClient(Boolean generateClient) {
        this.generateClient = generateClient;
    }

    @Input
    @Optional
    @Override
    public String getRequestSuffix() {
        return requestSuffix;
    }

    public void setRequestSuffix(String requestSuffix) {
        this.requestSuffix = requestSuffix;
    }

    @Input
    @Optional
    @Override
    public String getResponseSuffix() {
        return responseSuffix;
    }

    public void setResponseSuffix(String responseSuffix) {
        this.responseSuffix = responseSuffix;
    }

    @Input
    @Optional
    @Override
    public String getResponseProjectionSuffix() {
        return responseProjectionSuffix;
    }

    public void setResponseProjectionSuffix(String responseProjectionSuffix) {
        this.responseProjectionSuffix = responseProjectionSuffix;
    }

    @Input
    @Optional
    @Override
    public String getParametrizedInputSuffix() {
        return parametrizedInputSuffix;
    }

    public void setParametrizedInputSuffix(String parametrizedInputSuffix) {
        this.parametrizedInputSuffix = parametrizedInputSuffix;
    }

    @Input
    @Optional
    @Override
    public Set<String> getUseObjectMapperForRequestSerialization() {
        return useObjectMapperForRequestSerialization;
    }

    public void setUseObjectMapperForRequestSerialization(Set<String> useObjectMapperForRequestSerialization) {
        this.useObjectMapperForRequestSerialization = useObjectMapperForRequestSerialization;
    }

    @Input
    @Optional
    @Override
    public Boolean getGenerateAllMethodInProjection() {
        return generateAllMethodInProjection;
    }

    public void setGenerateAllMethodInProjection(boolean generateAllMethodInProjection) {
        this.generateAllMethodInProjection = generateAllMethodInProjection;
    }

    @Input
    @Optional
    @Override
    public Integer getResponseProjectionMaxDepth() {
        return responseProjectionMaxDepth;
    }

    public void setResponseProjectionMaxDepth(int responseProjectionMaxDepth) {
        this.responseProjectionMaxDepth = responseProjectionMaxDepth;
    }

    @Nested
    @Optional
    public ParentInterfacesConfig getParentInterfaces() {
        return parentInterfaces;
    }

    public void parentInterfaces(Action<? super ParentInterfacesConfig> action) {
        action.execute(parentInterfaces);
    }

    @Internal
    @Override
    public String getQueryResolverParentInterface() {
        return parentInterfaces.getQueryResolver();
    }

    @Internal
    @Override
    public String getMutationResolverParentInterface() {
        return parentInterfaces.getMutationResolver();
    }

    @Internal
    @Override
    public String getSubscriptionResolverParentInterface() {
        return parentInterfaces.getSubscriptionResolver();
    }

    @Internal
    @Override
    public String getResolverParentInterface() {
        return parentInterfaces.getResolver();
    }

    @InputFiles
    @Optional
    public List<String> getConfigurationFiles() {
        return configurationFiles;
    }

    public void setConfigurationFiles(List<String> configurationFiles) {
        this.configurationFiles = configurationFiles;
    }

    @Input
    @Optional
    @Override
    public GeneratedLanguage getGeneratedLanguage() {
        return generatedLanguage;
    }

    public void setGeneratedLanguage(GeneratedLanguage generatedLanguage) {
        this.generatedLanguage = generatedLanguage;
    }

    @Input
    @Optional
    @Override
    public Boolean isGenerateModelOpenClasses() {
        return generateModelOpenClasses;
    }

    public void setGenerateModelOpenClasses(Boolean generateModelOpenClasses) {
        this.generateModelOpenClasses = generateModelOpenClasses;
    }

    @Input
    @Optional
    @Override
    public Boolean isInitializeNullableTypes() {
        return initializeNullableTypes;
    }

    public void setInitializeNullableTypes(Boolean initializeNullableTypes) {
        this.initializeNullableTypes = initializeNullableTypes;
    }

    @Input
    @Optional
    @Override
    public Boolean isGenerateSealedInterfaces() {
        return generateSealedInterfaces;
    }

    public void setGenerateSealedInterfaces(Boolean generateSealedInterfaces) {
        this.generateSealedInterfaces = generateSealedInterfaces;
    }

    @Input
    @Optional
    @Override
    public Boolean isSupportUnknownFields() {
        return supportUnknownFields;
    }

    public void setSupportUnknownFields(boolean supportUnknownFields) {
        this.supportUnknownFields = supportUnknownFields;
    }

    @Input
    @Optional
    @Override
    public String getUnknownFieldsPropertyName() {
        return unknownFieldsPropertyName;
    }

    public void setUnknownFieldsPropertyName(String unknownFieldsPropertyName) {
        this.unknownFieldsPropertyName = unknownFieldsPropertyName;
    }

    @Input
    @Optional
    public Boolean isSkip() {
        return skip;
    }

    public void setSkip(Boolean skip) {
        this.skip = skip;
    }

}

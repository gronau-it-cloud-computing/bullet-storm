/*
 *  Copyright 2016, Yahoo Inc.
 *  Licensed under the terms of the Apache License, Version 2.0.
 *  See the LICENSE file associated with the project for terms.
 */
package com.yahoo.bullet.parsing;

import com.google.gson.annotations.Expose;
import com.yahoo.bullet.BulletConfig;
import com.yahoo.bullet.operations.AggregationOperations;
import com.yahoo.bullet.operations.AggregationOperations.AggregationType;
import com.yahoo.bullet.operations.AggregationOperations.GroupOperationType;
import com.yahoo.bullet.operations.aggregations.GroupOperation;
import com.yahoo.bullet.operations.aggregations.Strategy;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.bullet.operations.AggregationOperations.isEmpty;
import static com.yahoo.bullet.parsing.Error.makeError;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@Getter @Setter
public class Aggregation implements Configurable, Validatable {
    @Expose
    private Integer size;
    @Expose
    private AggregationType type;
    @Expose
    private Map<String, Object> attributes;
    @Expose
    private List<String> fields;

    @Setter(AccessLevel.NONE)
    private Set<GroupOperation> groupOperations;

    @Setter(AccessLevel.NONE)
    private Strategy strategy;

    // In case, any strategies need it.
    private Map configuration;

    // We only support COUNT for now.
    public static final Set<AggregationType> SUPPORTED_AGGREGATION_TYPES = new HashSet<>(asList(AggregationType.GROUP,
                                                                                                AggregationType.RAW));
    public static final List<GroupOperationType> SUPPORTED_GROUP_OPERATIONS = singletonList(GroupOperationType.COUNT);

    public static final String TYPE_NOT_SUPPORTED_ERROR_PREFIX = "Aggregation type not supported";
    public static final String TYPE_NOT_SUPPORTED_RESOLUTION = "Current supported aggregation types are: RAW, GROUP";

    // Temporary
    public static final Error GROUP_FIELDS_NOT_SUPPORTED_ERROR = makeError("Group type aggregation cannot have fields",
                                                                           "Do not specify fields when type is GROUP");
    // Temporary
    public static final Error GROUP_ALL_OPERATION_ERROR = makeError("Group all needs to specify an operation to do",
                                                                    "Currently supported operations are: COUNT");

    public static final Integer DEFAULT_SIZE = 1;
    public static final Integer DEFAULT_MAX_SIZE = 30;

    public static final String OPERATIONS = "operations";
    public static final String OPERATION_TYPE = "type";
    public static final String OPERATION_FIELD = "field";
    public static final String OPERATION_NEW_NAME = "newName";

    /**
     * Default constructor. GSON recommended
     */
    public Aggregation() {
        type = AggregationType.RAW;
    }

    @Override
    public void configure(Map configuration) {
        this.configuration = configuration;

        Number defaultSize = (Number) configuration.getOrDefault(BulletConfig.AGGREGATION_DEFAULT_SIZE, DEFAULT_SIZE);
        Number maximumSize = (Number) configuration.getOrDefault(BulletConfig.AGGREGATION_MAX_SIZE, DEFAULT_MAX_SIZE);
        int sizeDefault = defaultSize.intValue();
        int sizeMaximum = maximumSize.intValue();

        // Null or negative, then default, else min of size and max
        size = (size == null || size < 0) ? sizeDefault : Math.min(size, sizeMaximum);

        // Parse any group operations first before calling getStrategy
        groupOperations = getOperations();

        strategy = AggregationOperations.getStrategyFor(this);
    }

    @Override
    public Optional<List<Error>> validate() {
        if (type == AggregationType.GROUP) {
            // We only support GROUP by ALL for now
            if (!isEmpty(fields)) {
                return Optional.of(singletonList(GROUP_FIELDS_NOT_SUPPORTED_ERROR));
            }
            // Group operations are only created if they are supported.
            if (isEmpty(groupOperations)) {
                return Optional.of(singletonList(GROUP_ALL_OPERATION_ERROR));
            }
        }
        // Supported aggregation types should be documented in TYPE_NOT_SUPPORTED_RESOLUTION
        if (!SUPPORTED_AGGREGATION_TYPES.contains(type)) {
            String typeSuffix = type == null ? "" : ": " + type;
            return Optional.of(singletonList(makeError(TYPE_NOT_SUPPORTED_ERROR_PREFIX + typeSuffix,
                                                       TYPE_NOT_SUPPORTED_RESOLUTION)));
        }
        return Optional.empty();
    }

    private Set<GroupOperation> getOperations() {
        if (isEmpty(attributes)) {
            return null;
        }
        return parseOperations(attributes.get(OPERATIONS));
    }

    private Set<GroupOperation> parseOperations(Object object) {
        if (object == null) {
            return null;
        }
        try {
            // Unchecked cast needed.
            List<Map<String, String>> operations = (List<Map<String, String>>) object;
            // Return a list of distinct, non-null, GroupOperations
            return operations.stream().map(Aggregation::makeGroupOperation)
                                      .filter(Objects::nonNull)
                                      .collect(Collectors.toSet());
        } catch (ClassCastException cce) {
            return null;
        }
    }

    private static GroupOperation makeGroupOperation(Map<String, String> data) {
        String type = data.get(OPERATION_TYPE);
        Optional<GroupOperationType> operation = SUPPORTED_GROUP_OPERATIONS.stream().filter(t -> t.isMe(type)).findFirst();
        // May or may not be present
        String field = data.get(OPERATION_FIELD);
        // May or may not be present
        String newName = data.get(OPERATION_NEW_NAME);
        // Unknown GroupOperations are ignored.
        return operation.isPresent() ? new GroupOperation(operation.get(), field, newName) : null;
    }
}

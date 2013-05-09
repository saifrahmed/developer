/*******************************************************************************
 * Copyright 2013 Open mHealth
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package org.openmhealth.reference.mongodb.data;

import java.util.LinkedList;
import java.util.List;

import org.mongojack.DBCursor;
import org.mongojack.DBQuery;
import org.mongojack.DBQuery.Query;
import org.mongojack.JacksonDBCollection;
import org.mongojack.internal.MongoJacksonMapperModule;
import org.openmhealth.reference.concordia.OmhValidationController;
import org.openmhealth.reference.data.Registry;
import org.openmhealth.reference.domain.MultiValueResult;
import org.openmhealth.reference.domain.Schema;
import org.openmhealth.reference.mongodb.domain.MongoMultiValueResult;
import org.openmhealth.reference.mongodb.domain.MongoSchema;

import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

/**
 * <p>
 * The interface to the database-backed Registry.
 * </p>
 * 
 * @author John Jenkins
 */
public class MongoRegistry extends Registry {
	/**
	 * The object mapper that should be used to parse {@link Schema}s.
	 */
	private static final ObjectMapper JSON_MAPPER;
	static {
		// Create the object mapper.
		ObjectMapper mapper = new ObjectMapper();
		
		// Add our custom validation controller as an injectable parameter to
		// the Schema's constructor.
		InjectableValues.Std injectableValues = new InjectableValues.Std();
		injectableValues
			.addValue(
				Schema.JSON_KEY_VALIDATION_CONTROLLER,
				OmhValidationController.VALIDATION_CONTROLLER);
		mapper.setInjectableValues(injectableValues);
		
		// Finally, we must configure the mapper to work with the MongoJack
		// configuration.
		JSON_MAPPER = MongoJacksonMapperModule.configure(mapper);
	}
	
	/**
	 * Default constructor.
	 */
	protected MongoRegistry() {
		// Get the collection to add indexes to.
		DBCollection collection =
			MongoDao.getInstance().getDb().getCollection(DB_NAME);

		// Ensure that there is an index on the ID.
		collection
			.ensureIndex(
				new BasicDBObject(Schema.JSON_KEY_ID, 1),
				DB_NAME + "_" + Schema.JSON_KEY_ID + "_index",
				false);
		// Ensure that there is an index on the version.
		collection
			.ensureIndex(
				new BasicDBObject(Schema.JSON_KEY_VERSION, 1),
				DB_NAME + "_" + Schema.JSON_KEY_VERSION + "_index",
				false);
		
		// Ensure that there is a compound, unique key on the ID and version.
		collection
			.ensureIndex(
				(new BasicDBObject(Schema.JSON_KEY_ID, 1))
					.append(Schema.JSON_KEY_VERSION, 1),
				DB_NAME + 
					"_" + 
					Schema.JSON_KEY_ID + 
					"_" + 
					Schema.JSON_KEY_VERSION + 
					"_unique",
				true);
	}

	/*
	 * (non-Javadoc)
	 * @see org.openmhealth.reference.data.Registry#getSchemas(java.lang.String, java.lang.Long, long, long)
	 */
	@Override
	public MultiValueResult<? extends Schema> getSchemas(
		final String schemaId, 
		final Long schemaVersion,
		final long numToSkip,
		final long numToReturn) {
		
		// Get the connection to the database.
		DB db = MongoDao.getInstance().getDb();
		
		// Get the connection to the registry with the Jackson wrapper.
		JacksonDBCollection<MongoSchema, Object> collection =
			JacksonDBCollection
				.wrap(
					db.getCollection(DB_NAME),
					MongoSchema.class,
					Object.class,
					JSON_MAPPER);
		
		// Create the fields to limit the query.
		List<Query> queries = new LinkedList<Query>();
		
		// Add the schema ID, if given.
		if(schemaId != null) {
			queries.add(DBQuery.is(MongoSchema.JSON_KEY_ID, schemaId));
		}
		
		// Add the schema version, if given.
		if(schemaVersion != null) {
			queries
				.add(DBQuery.is(MongoSchema.JSON_KEY_VERSION, schemaVersion));
		}
		
		// Build the query based on the number of parameters.
		DBCursor<MongoSchema> result;
		if(queries.size() == 0) {
			result = collection.find();
		}
		else {
			result =
				collection.find(DBQuery.and(queries.toArray(new Query[0])));
		}

		// Build the sort field.
		DBObject sort = new BasicDBObject();
		sort.put(MongoSchema.JSON_KEY_ID, -1);
		sort.put(MongoSchema.JSON_KEY_VERSION, -1);
		
		return
			new MongoMultiValueResult<MongoSchema>(
				result
					.sort(sort)
					.skip((new Long(numToSkip)).intValue())
					.limit((new Long(numToReturn)).intValue()));
	}
}
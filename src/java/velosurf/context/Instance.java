/*
 * Copyright 2003 The Apache Software Foundation.
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
 * limitations under the License.
 */

package velosurf.context;

import java.sql.SQLException;
import java.util.*;

import velosurf.model.Action;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.UserContext;

/** An Instance provides field values by their name.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public class Instance extends TreeMap<String,Object>
{
    /** Build an empty instance for the given entity.
     *
     * @param entity Entity this instance is a realisation of
     */
    public Instance(Entity entity) {
        initialize(entity);
    }

    /**
     * Builds a generic instance using <code>values</code>.
     * @param values
     */
    public Instance(Map<String,Object> values) {
        for(Object key:values.keySet()) {
            put(Database.adaptContextCase((String)key),values.get(key));
        }
    }

     /** Initialization. Meant to be overloaded if needed.
      * @param entity
      */
     public void initialize(Entity entity) {
         this.entity = entity;
         db = this.entity.getDB();
         localized = this.entity.hasLocalizedColumns();
     }

    /** Get this Instance's Entity.
     *
     * @return this Instance's Entity.
     */
    public EntityReference getEntity() {
        return new EntityReference(entity);
    }

    /** <p>Returns an ArrayList of two-entries maps ('name' & 'value'), meant to be use in a #foreach loop to build form fields.</p>
     * <p>Example:</p>
     * <code>
     * #foreach ($field in $product.primaryKey)<br>
     * &nbsp;&nbsp;&lt;input type=hidden name='$field.name' value='$field.value'&gt;<br>
     * #end</code>
     *
     * @return an ArrayList of two-entries maps ('name' & 'value')
     */
    public List getPrimaryKey() {
        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        if (entity!=null) {
            for (Iterator i=entity.getPKCols().iterator();i.hasNext();) {
                String key = (String)i.next();
                Map<String,Object> map = new HashMap<String,Object>();
                map.put("name",key);
                map.put("value",getInternal(key));
                result.add(map);
            }
        }
        return result;
    }

    /** <p>Generic getter, used to access this instance properties by their name.</p>
     * <p>Asked property is first searched in the Map, then among Attributes defined for the entity.</p>
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if an error
     *      occurs
     */
    public Object get(String key) {
        if (db != null) {
            key = db.adaptCase((String)key);
        }
        Object result = null;
        try {
            result = super.get(key);
            if (result == null) {
                if (entity!=null) {
                    Attribute attribute = entity.getAttribute(key);
                    if (attribute != null)
                        switch (attribute.getType()) {
                            case Attribute.ROWSET:
                                result = new AttributeReference(this,attribute);
                                // then cache it in the map, so that order and refinement will work later in the same context
                                super.put(key,result);
                                break;
                            case Attribute.ROW:
                                result = attribute.fetch(this);
                                break;
                            case Attribute.SCALAR:
                                result = attribute.evaluate(this);
                                break;
                            default:
                                Logger.error("Unknown attribute type for "+entity.getName()+"."+key+"!");
                        }
                    else {
                        Action action = entity.getAction(key);
                        if (action != null) result = Integer.valueOf(action.perform(this));
                    }
                }
            } else if (localized && entity.isLocalized(key)) {
                result = db.getUserContext().localize(result.toString());
            }
        }
        catch (SQLException sqle) {
            handleSQLException(sqle);
        }
        return result;
    }

    /** Generic setter.
     *
     * @param key key of the property to be set
     * @param value corresponding value
     * @return previous value, or null
     */
    public synchronized Object put(String key, Object value)
    {
        if(db != null) {
            key = db.adaptCase(key);
        }
        return super.put(key,value);
    }

    /** Internal getter. First tries on the external object then on the Map interface.
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getInternal(Object key) {
        Object ret = getExternal(key);
        if (ret == null) ret = super.get(key);
        return ret;
    }

    /** External getter. Meant to be overloaded in ExternalObjectWrapper.
     *
     * @param key key of the property to be returned
     * @return a String, an Instance, an AttributeReference or null if not found or if an error
     *      occurs
     */
    public Object getExternal(Object key) {
        return null;
    }

    /**
     * Test equality of two instances.
     * @param o other instance
     * @return equality status
     */
    public boolean equals(Object o) {
        return super.equals(o);
/*
            if (entity != null) {
                // compare only keys
                List keys = entity.getKeys();
                Instance other = (Instance)o;
                if (keys.size() > 0) {
                    for(Iterator i = keys.iterator();i.hasNext();) {
                        if (getInternal(i.next()) != other.getInternal(i.next()))
                            return false;
                    }
                    return true;
                }
            }
*/
    }

    /** <p>Update the row associated with this Instance from passed values.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean update() {
        return update(null);
    }

    /** <p>Update the row associated with this Instance from actual values.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @param values values to be used for the update
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *      occurs (in which case $db.error can be checked).
     */
    public synchronized boolean update(Map<String,Object> values) {
        try {
            if (entity == null) {
                throw new SQLException("Cannot update an instance whose Entity is null.");
            }
            if (entity.isReadOnly()) {
                throw new SQLException("Entity "+entity.getName()+" is read-only.");
            }
            Map<String,Object> newvalues = new HashMap<String,Object>();
            for(Iterator it = keySet().iterator();it.hasNext();) {
                String key = (String)it.next();
                newvalues.put(db.adaptCase(key),getInternal(key));
            }
            if (values != null && values != this) {
                for(Map.Entry<String,Object> entry:values.entrySet()) {
                    newvalues.put(db.adaptCase(entry.getKey()),entry.getValue());
                }
            }
            List<String> updateClause = new ArrayList<String>();
            List<String> whereClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            List<String> cols = new ArrayList<String>(entity.getColumns());
            cols.removeAll(entity.getPKCols());
            for (String col:cols) {
                Object value = newvalues.get(col);
                if (value!=null) {
                    updateClause.add(entity.aliasToColumn(col)+"=?");
                    if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
                    params.add(value);
                }
            }
            for (String col:entity.getPKCols()) {
                Object value = newvalues.get(col);
                if (value == null) throw new SQLException("field '"+col+"' belongs to primary key and cannot be null!");
                if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
//                if (entity.isLocalized(col)) value = entity.unlocalize(value); ???
                whereClause.add(entity.aliasToColumn(col)+"=?");
                params.add(value);
            }
            String query = "update "+entity.getTableName()+" set "+StringLists.join(updateClause,",")+" where "+StringLists.join(whereClause," and ");
            PooledPreparedStatement statement = db.prepare(query);
            int nb = statement.update(params);
            if (nb==0) {
                Logger.warn("query \""+query+"\" affected 0 row...");
            }
            else if (nb>1) { // ?!?! Referential integrities on key columns should avoid this...
                throw new SQLException("query \""+query+"\" affected more than 1 rows!");
            } else {
                /* invalidate cache */
                if (entity != null) {
                    entity.invalidateInstance(this);
                }
            }
            return true;
        }
        catch (SQLException sqle) {
            handleSQLException(sqle);
            return false;
        }
    }

    /** <p>Delete the row associated with this Instance.</p>
     * <p>Velosurf will ensure all key columns are specified, to avoid an accidental massive update.</p>
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean delete() {
        try {
            if (entity == null) throw new SQLException("Instance.delete: Error: Entity is null!");
            List<String> whereClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            for (String col:entity.getPKCols()) {
                Object value = getInternal(col);
                if (value == null) throw new SQLException("Instance.delete: Error: field '"+col+"' belongs to primary key and cannot be null!");
                if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
                whereClause.add(entity.aliasToColumn(col)+"=?");
                params.add(value);
            }
            String query = "delete from "+entity.getTableName()+" where "+StringLists.join(whereClause," and ");
            PooledPreparedStatement statement = db.prepare(query);
            int nb = statement.update(params);
            if (nb==0) {
                Logger.warn("query \""+query+"\" affected 0 row...");
            }
            else if (nb>1) // ?!?! Referential integrities on key columns should avoid this...
                throw new SQLException("query \""+query+"\" affected more than 1 rows!");
            else {
                /* invalidate cache */
                if (entity != null) {
                    entity.invalidateInstance(this);
                }
            }
            return true;
        }
        catch (SQLException sqle) {
            handleSQLException(sqle);
            return false;
        }
    }

    /** Insert a new row corresponding to this Instance.
     *
     * @return <code>true</code> if successfull, <code>false</code> if an error
     *     occurs (in which case $db.error can be checked).
     */
    public synchronized boolean insert() {
        try {
            if (entity == null) {
                throw new SQLException("Instance.insert: Error: Entity is null!");
            }

            if (!entity.validate(this)) {
                return false;
            }
            List<String> colsClause = new ArrayList<String>();
            List<String> valsClause = new ArrayList<String>();
            List<Object> params = new ArrayList<Object>();
            List<String> cols = entity.getColumns();
            for (String col:cols) {
                Object value = getInternal(col);
                if (value!=null) {
                    colsClause.add(entity.aliasToColumn(col));
                    valsClause.add("?");
                    if (entity.isObfuscated(col)) value = entity.deobfuscate(value);
                    params.add(value);
                }
            }
            String query = "insert into "+entity.getTableName()+" ("+StringLists.join(colsClause,",")+") values ("+StringLists.join(valsClause,",")+")";
            PooledPreparedStatement statement = db.prepare(query);
            statement.update(params);
            List<String> keys = entity.getPKCols();
            if (keys.size() == 1) {
                /* What if the ID is not autoincremented? TODO check it. => reverse engineering of autoincrement, and set the value in the instance itself */
                db.getUserContext().setLastInsertedID(entity,statement.getLastInsertID());
            }
            return true;
        }
        catch (SQLException sqle) {
            handleSQLException(sqle);
            return false;
        }
    }

    /** Validate this instance against declared contraints.
     * @return a boolean stating whether this instance data are valid in regard to declared constraints
     */
    public boolean validate() {
        try {
            return entity.validate(this);
        } catch(SQLException sqle) {
            handleSQLException(sqle);
            return false;
        }
    }

    /** Handle an sql exception.
     *
     */
    private void handleSQLException(SQLException sqle) {
        Logger.log(sqle);
        db.setError(sqle.getMessage());
    }

    /** This Instance's Entity.
     */
    private Entity entity = null;

    /** Is there a column to localize?
     */
    private boolean localized = false;

    /** The main database connection.
     */
    private Database db = null;

}

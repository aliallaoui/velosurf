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

package velosurf.web;

import java.util.*;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ParameterParser;

import velosurf.util.Logger;
import velosurf.model.Entity;

/** This class extends the tool org.apache.velocity.tools.view.tools.ParameterParser,
 *  adding a generic setter. Values that are set manually hide any previous values that
 *  are present in the query under the same key.
 *
 * It is meant for the query scope.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/
 public class HttpQueryTool extends ParameterParser implements Map
{

    private ViewContext context = null;
    private Map extraValues = new HashMap();

    public HttpQueryTool() {
    }

    public void init(Object inViewContext) {

        super.init(inViewContext);

        if (!(inViewContext instanceof ViewContext)) {
            Logger.error("HttpQueryTool.init: can't initialize... bad scope ? (query scope expected)");
            throw new IllegalArgumentException("expecting a ViewContext argument");
        }

        context = (ViewContext)inViewContext;
        HttpServletRequest request = context.getRequest();
    }

    public Object get(Object key)
    {
        Object ret = extraValues.get(key);
        if (ret == null) {
            return super.get(key.toString());
        } else {
            return ret;
        }
    }

    public Object put(Object key, Object value) {
        return extraValues.put(key,value);
    }

    public int size() {
        return context.getRequest().getParameterMap().size() + extraValues.size();
    }

    public boolean isEmpty() {
        return context.getRequest().getParameterMap().isEmpty() && extraValues.isEmpty();
    }

    public boolean containsKey(Object key) {
        return context.getRequest().getParameterMap().containsKey(key) || extraValues.containsKey(key);
    }

    public boolean containsValue(Object value) {
        String[] array = new String[1];
        array[0] = (String)value;
        return context.getRequest().getParameterMap().containsValue(array) || extraValues.containsValue(value);
    }

    public Object remove(Object key) {
        return extraValues.remove(key);
    }

    public void putAll(Map map) {
        extraValues.putAll(map);
    }

    public void clear() {
        extraValues.clear();
    }

    public Set keySet() {
        Set ret = context.getRequest().getParameterMap().keySet();
        ret.addAll(extraValues.keySet());
        return ret;
    }

    public Collection values() {
        String[] array;
        Collection ret = new HashSet();
        Collection coll = context.getRequest().getParameterMap().values();
        for(Object value:coll) {
            array=(String[])value;
            ret.add( array.length==1 ?  (Object)array[0] : array);
        }
        ret.addAll(extraValues.values());
        return ret;
    }

    public Set<Entry> entrySet() {
        Set<Entry> ret = context.getRequest().getParameterMap().entrySet();
        ret.addAll(extraValues.entrySet());
        return ret;
    }
}

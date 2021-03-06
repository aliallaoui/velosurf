package velosurf.model;

import velosurf.context.EntityAccessor;
import velosurf.context.Instance;
import velosurf.util.Logger;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EventsQueue implements Runnable
{

  private Queue<Event> queue = null;

  private static final int SLEEP_DELAY = 1000;

  public EventsQueue()
  {
    queue = new ConcurrentLinkedQueue<Event>();
  }

  @Override
  public void run()
  {
    while (true)
    {
      Event event;
      while ((event = queue.poll()) != null)
      {
        try
        {
          Entity entity = EntityAccessor.getInstanceEntity(event.instance);
          entity.dispatchEvent(event);
        }
        catch (Exception e)
        {
          Logger.error("Exception while dispatching db event");
          Logger.log(e);
        }
        if (Thread.currentThread().isInterrupted())
        {
          Logger.debug("db events queue has been stopped");
          break;
        }
      }
      try
      {
        Thread.currentThread().sleep(SLEEP_DELAY);
      }
      catch (InterruptedException ie)
      {
        break;
      }
    }
  }

  public void post(Event event) { queue.add(event); }

  public enum EventType { INSERT, UPDATE, DELETE }

  public static class Event
  {
      EventType type;
      Instance instance;
      Set<String> fields;
      public Event(EventType type, Instance instance) { this(type, instance, null); }
      public Event(EventType type, Instance instance, Set<String> fields) { this.type = type; this.instance = instance; this.fields = fields; }
  }
}

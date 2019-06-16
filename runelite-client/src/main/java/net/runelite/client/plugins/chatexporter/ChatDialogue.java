package net.runelite.client.plugins.chatexporter;

import com.google.gson.JsonObject;
import javafx.util.Pair;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatDialogue
{

    private static int index = 0;

    public static void resetIndex(){
        index = 0;
    }

    private int id = index++;

    enum ChatType
    {
        SELECT(219), PLAYER(217), NPC(231), SPRITE(193), SPRITE2(11), UNKNOWN(-1);
        int type;

        ChatType(int type)
        {
            this.type = type;
        }

        public static ChatType getType(int id)
        {
            for(ChatType type : ChatType.values())
            {
                if(type.type == id){
                    return type;
                }
            }
            return ChatType.UNKNOWN;
        }
    }

    private ChatType type;
    private Map<Integer, Pair<String, ChatDialogue>> options;
    private ChatDialogue next;
    private String title;
    private String text;

    public ChatDialogue(ChatType type)
    {
        this.type = type;
    }

    public JsonObject toJSON()
    {
        JsonObject json = new JsonObject();
        json.addProperty("id", id);
        json.addProperty("type", type.name());
        json.addProperty("title", convertString(title));
        json.addProperty("text", convertString(text));
        json.addProperty("next", next != null ? next.getID() : null);
        if(options != null)
        {
            for(Map.Entry<Integer, Pair<String, ChatDialogue>> entry : options.entrySet())
            {
                JsonObject option = new JsonObject();
                json.add("" + entry.getKey(), option);
                Pair<String, ChatDialogue> pair = entry.getValue();
                option.addProperty("text", convertString(pair.getKey()));
                option.addProperty("value", pair.getValue() != null ? pair.getValue().getID() : null);
            }
        }
        return json;
    }

    public int getID()
    {
        return id;
    }

    public ChatType getType()
    {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public void setOption(int index, ChatDialogue to)
    {
        options.put(index, new Pair<>(options.get(index).getKey(), to));
    }

    public String getFirstMissing()
    {
        Map.Entry<Integer, Pair<String, ChatDialogue>> entry = options.entrySet().stream().filter(e -> e.getValue().getValue()==null).findFirst().orElse(null);
        return entry != null ? id + ": " + entry.getValue().getKey() : null;
    }

    public void setNext(ChatDialogue dialogue)
    {
        this.next = dialogue;
    }

    public ChatDialogue getNext()
    {
        return next;
    }

    public void parse(Widget widget)
    {
        switch(this.type)
        {
            case SELECT:
            {
                options = new HashMap<>();
                Widget[] childComponents = widget.getDynamicChildren();
                if (childComponents != null)
                {
                    int index = 0;
                    for (Widget component : childComponents)
                    {
                        if(component.getType()==4)
                        {
                            if(!component.hasListener())
                            {
                                title = component.getText();
                            }
                            else
                            {
                                Pair pair = new Pair<>(component.getText(), null);
                                options.put(index++, pair);
                            }
                        }
                    }
                }
                break;
            }
            case SPRITE:
            case SPRITE2:
            case PLAYER:
            case NPC:
            {
                Widget[] childComponents = widget.getStaticChildren();
                if (childComponents != null)
                {
                    for (Widget component : childComponents)
                    {
                        if(component.getType() == 4)
                        {
                            if(component.getTextColor() == 0)
                            {
                                text = component.getText();
                            }
                            else if(component.getTextColor() == 8388608)
                            {
                                title = component.getText();
                            }
                        }
                    }
                }
                break;
            }
        }
    }


    public boolean check(Widget widget, ChatType type)
    {
        if(type != this.type)
        {
            return false;
        }
        switch(this.type)
        {
            case SELECT:
            {
                Widget[] childComponents = widget.getDynamicChildren();
                if (childComponents != null)
                {
                    int index = 0;
                    for (Widget component : childComponents)
                    {
                        if(component.getType()==4)
                        {
                            if(!component.hasListener())
                            {
                                if(!this.title.equals(component.getText()))
                                {
                                    return false;
                                }
                            }
                            else
                            {
                                Pair<String, ChatDialogue> pair = options.get(index++);
                                if(!component.getText().equals(pair.getKey()))
                                {
                                    return false;
                                }
                            }
                        }
                    }
                }
                break;
            }
            case SPRITE:
            case SPRITE2:
            case PLAYER:
            case NPC:
            {
                Widget[] childComponents = widget.getStaticChildren();
                if (childComponents != null)
                {
                    for (Widget component : childComponents)
                    {
                        if(component.getType() == 4 && component.getTextColor() == 0)
                        {
                            if(!text.equals(component.getText()))
                            {
                                return false;
                            }
                        }
                    }
                }
                break;
            }
        }
        return true;
    }

    private String convertString(String string)
    {
        if(string==null)
        {
            return null;
        }
        return string.replaceAll("<br>", "\n");
    }
}

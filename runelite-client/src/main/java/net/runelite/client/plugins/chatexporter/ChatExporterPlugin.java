/*
 * Copyright (c) 2018, Hydrox6 <ikada@protonmail.ch>
 * Copyright (c) 2018, Adam <Adam@sigterm.info>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.chatexporter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import net.runelite.api.Client;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.hiscore.HiscorePanel;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@PluginDescriptor(
	name = "Chat Exporter",
	description = "Exports dialogue",
	tags = {"messages"},
	enabledByDefault = true
)
public class ChatExporterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientToolbar clientToolbar;

	private ChatPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp() throws Exception {
		panel = injector.getInstance(ChatPanel.class);

		final BufferedImage icon = ImageUtil.getResourceStreamFromClass(getClass(), "normal.png");

		navButton = NavigationButton.builder()
				.tooltip("Export")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	protected void reset()
	{
		base = null;
		last = null;
		all = new ArrayList<ChatDialogue>();
		ChatDialogue.resetIndex();
		panel.updateInfo("Size: " + all.size());
	}

	protected void export()
	{
		JFileChooser chooser = new JFileChooser();
		FileNameExtensionFilter filter = new FileNameExtensionFilter("JSON files", "json");
		chooser.setFileFilter(filter);
		int returnVal = chooser.showOpenDialog(null);
		if(returnVal == JFileChooser.APPROVE_OPTION)
		{
			String filename = chooser.getSelectedFile().getName();
			if(!filename.toLowerCase().endsWith(".json"))
			{
				filename += ".json";
			}
			File to = new File(chooser.getSelectedFile().getParentFile(), filename);
			try
			{
				Gson gson = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().serializeNulls().create();
				PrintWriter pw = new PrintWriter(to);
				pw.println(gson.toJson(toJSON()));
				pw.close();
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	private int lastClicked = -1;
	private List<ChatDialogue> all = new ArrayList<ChatDialogue>();
	private ChatDialogue base;
	private ChatDialogue last;

	public JsonArray toJSON(){
		JsonArray jsonArray = new JsonArray();
		for(ChatDialogue cd : all){
			jsonArray.add(cd.toJSON());
		}
		return jsonArray;
	}
	private ChatDialogue searchOrCreate(Widget widget, ChatDialogue.ChatType type)
	{
		for(ChatDialogue dialogue : all)
		{
			if(dialogue.check(widget, type))
			{
				return dialogue;
			}
		}
		return create(widget, type);
	}

	private ChatDialogue create(Widget widget, ChatDialogue.ChatType type)
	{
		ChatDialogue chat = new ChatDialogue(type);
		chat.parse(widget);
		all.add(chat);
		panel.updateInfo("Size: " + all.size());
		if(chat.getType() == ChatDialogue.ChatType.NPC){
			panel.updateName(chat.getTitle());
		}
		return chat;
	}

	private String getMissing()
	{
		for(ChatDialogue dialogue : all)
		{
			if(dialogue.getType() == ChatDialogue.ChatType.SELECT){
				String missing = dialogue.getFirstMissing();
				if(missing != null)
				{
					return missing;
				}
			}
		}
		return "No missing";
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked click)
	{
		ChatDialogue.ChatType type = ChatDialogue.ChatType.getType(WidgetInfo.TO_GROUP(click.getWidgetId()));
		switch(type)
		{
			case SPRITE:
			case SPRITE2:
			case SELECT:
			case PLAYER:
			case NPC:
			{
				// Use to get widget
				int id = click.getId();
				Widget widget = client.getWidget(WidgetInfo.TO_GROUP(id), WidgetInfo.TO_CHILD(id));
				//INDEX
				lastClicked = click.getActionParam();
				panel.updateMissing(getMissing());
				break;
			}
			default:{
				// This is used for when the dialogue is closed
				last = null;
			}
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded loaded)
	{
		ChatDialogue.ChatType type = ChatDialogue.ChatType.getType(loaded.getGroupId());
		switch(type)
		{
			case SPRITE:
			case SPRITE2:
			case SELECT:
			case PLAYER:
			case NPC:
			{
				int child = loaded.getGroupId() == 219 ? 1 : 0;
				Widget widget = client.getWidget(loaded.getGroupId(), child);
				// This is required as the dynamic values don't load until after
				Timer timer = new Timer(100, new ActionListener()
				{
					@Override
					public void actionPerformed(ActionEvent arg0)
					{
						parse(widget, type);
					}
				});
				timer.setRepeats(false);
				timer.start();
				break;
			}
		}
	}

	private void parse(Widget widget, ChatDialogue.ChatType type)
	{
		ChatDialogue dialogue = null;
		if(base ==null)
		{
			dialogue = base = create(widget, type);
		}
		else
		{
			dialogue = searchOrCreate(widget, type);
		}
		if(last!=null)
		{
			if(last.getType() == ChatDialogue.ChatType.SELECT)
			{
				if(lastClicked != -1)
				{
					last.setOption(lastClicked-1, dialogue);
				}
			}
			else
			{
				last.setNext(dialogue);
			}
		}
		last = dialogue;
	}
}

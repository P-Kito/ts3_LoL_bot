import java.util.List;
import java.util.logging.Level;

import com.github.theholywaffle.teamspeak3.TS3Api;
import com.github.theholywaffle.teamspeak3.TS3Config;
import com.github.theholywaffle.teamspeak3.TS3Query;
import com.github.theholywaffle.teamspeak3.TS3Query.FloodRate;
import com.github.theholywaffle.teamspeak3.api.event.ChannelCreateEvent;
import com.github.theholywaffle.teamspeak3.api.event.ChannelDeletedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ChannelDescriptionEditedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ChannelEditedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ChannelMovedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ChannelPasswordChangedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ClientJoinEvent;
import com.github.theholywaffle.teamspeak3.api.event.ClientLeaveEvent;
import com.github.theholywaffle.teamspeak3.api.event.ClientMovedEvent;
import com.github.theholywaffle.teamspeak3.api.event.ServerEditedEvent;
import com.github.theholywaffle.teamspeak3.api.event.TS3EventType;
import com.github.theholywaffle.teamspeak3.api.event.TS3Listener;
import com.github.theholywaffle.teamspeak3.api.event.TextMessageEvent;

import net.rithms.riot.api.ApiConfig;
import net.rithms.riot.api.RiotApi;
import net.rithms.riot.api.RiotApiException;
import net.rithms.riot.api.endpoints.champion.dto.Champion;
import net.rithms.riot.api.endpoints.spectator.dto.CurrentGameInfo;
import net.rithms.riot.api.endpoints.spectator.dto.CurrentGameParticipant;
import net.rithms.riot.api.endpoints.summoner.dto.Summoner;
import net.rithms.riot.constant.Platform;

public class TS3_LoLBot 
{
	static final int DEFAULT_CHANNEL = 1;
	static final String HOSTNAME = "";
	static final String API_LOGIN = "";
	static final String API_PW = "";
	static String RIOT_API_KEY = "";
	static final Platform SERVER = Platform.EUW;
	
	public static int getPartySizeForQueueId(int gameQueue)
	{
		switch (gameQueue)
		{
		case 8: 	// Normal 3v3
		case 41: 	// Ranked 3v3
		case 9: 	// Flex 3v3
			return 3;
		case 2:		// Normal Blind 5v5
		case 14:	// Normal Draft 5v5
		case 42:	// Ranked Team 5v5
		case 6: 	// Ranked Premade 5v5
		case 4:		// Ranked Solo 5v5
		case 400:	// Normal Draft 5v5 Pick Games
		case 410:	// Ranked Draft 5v5 Pick Games
		case 430:	// Normal Blind 5v5
		case 420:	// Ranked Solo Games that use Team builder (?)
		case 440:	// Ranked Flex Summoners Rift
		case 65: 	// ARAM
			return 5;
		default:
			return -1;
		}
	}
	
	public static void main(String[] args) throws RiotApiException
	{
		final TS3Config config = new TS3Config();
		config.setHost(HOSTNAME);
		config.setDebugLevel(Level.ALL);
		config.setFloodRate(FloodRate.UNLIMITED);

		TS3Query query = new TS3Query(config);
		query.connect();
	
		final TS3Api api = query.getApi();
		api.login(API_LOGIN , API_PW);
		api.selectVirtualServerById(1);
		api.setNickname("K-Teamspeak LoL Bot");
		api.moveClient(api.whoAmI().getId(), DEFAULT_CHANNEL);
				
		ApiConfig configRiot = new ApiConfig().setKey(RIOT_API_KEY);
		RiotApi LoL = new RiotApi(configRiot);
		
		api.registerAllEvents();
		api.registerEvent(TS3EventType.TEXT_CHANNEL, 0);
		api.addTS3Listeners(new TS3Listener()
		{
			@Override
			public void onTextMessage(TextMessageEvent e)
			{
				if (e.getMessage().contains("!key"))
				{
					String[] messageSplit = e.getMessage().split(" ");
					String key = "";
					for (int i = 1; i < messageSplit.length; i++)
					{
						if (i != 1) 
							key += " ";
						key += messageSplit[i];
					}
					
					RIOT_API_KEY = key;
					configRiot.setKey(RIOT_API_KEY);
					api.sendPrivateMessage(e.getInvokerId(), "Set key to " + key);
					
				}
				else if (e.getMessage().contains("!match"))
				{
					String[] messageSplit = e.getMessage().split(" ");
					String summonerName = "";
					if (messageSplit.length > 0)
					{
						for (int i = 1; i < messageSplit.length; i++)
						{
							if (i != 1) 
								summonerName += " ";
							summonerName += messageSplit[i];
						}
						
						Summoner summoner;
						
						try {
							summoner = LoL.getSummonerByName(SERVER, summonerName);
						} catch (RiotApiException e1) {
							api.sendChannelMessage("Could not find summoner " + summonerName);
							return;
						}
						try {
							CurrentGameInfo gameInfo = LoL.getActiveGameBySummoner(SERVER, summoner.getId());
							List<CurrentGameParticipant> gameParticipants = gameInfo.getParticipants();
							String message = "\n";
							int gameQueueType = getPartySizeForQueueId(gameInfo.getGameQueueConfigId());
							if (gameQueueType == -1)
							{
								api.sendChannelMessage("Summoner is in a non 5v5 or 3v3 gamemode");
								return;
							}
							for (int i = 0; i < gameParticipants.size(); i++)
							{
								Long summonerId = gameParticipants.get(i).getSummonerId();
								Summoner summonerInMatch = LoL.getSummoner(SERVER, summonerId);
								
								// Color of Team, hope API sends them ordered
								if (i < gameQueueType)
									message += "[COLOR=#0055ff][b]|[/b][/COLOR] ";
								else
									message += "[COLOR=#aa00ff][b]|[/b][/COLOR] ";
									
								// Name
								message += summonerName == summonerInMatch.getName() ? "[U]" + summonerInMatch.getName() + "[/U]" : summonerInMatch.getName();
								// Champion
								try {
									int chid = gameParticipants.get(i).getChampionId();
									Champion champ = LoL.getChampion(SERVER, chid);
									message += " - " + LoL.getDataChampion(SERVER, champ.getId()).getName();
								} catch (RiotApiException e1) {
									message += " - Unknown Champ";
								}
								// Elo
								try {
									message += " - " + LoL.getLeagueBySummonerId(SERVER, summonerId).get(0).getTier();
								} catch (RiotApiException e1) {
									message += " - Unranked?";
								}
								message += "\n";
							}
							api.sendChannelMessage(message);
						} catch (RiotApiException e1) {
							api.sendChannelMessage("Could not find match for " + summonerName);
						}
					}
				}
			}
			
			@Override
			public void onClientJoin(ClientJoinEvent e) 
			{
			}
			
			@Override
			public void onClientLeave(ClientLeaveEvent arg0)
			{
			}
			
			@Override
			public void onChannelCreate(ChannelCreateEvent arg0) 
			{
			}

			@Override
			public void onChannelDeleted(ChannelDeletedEvent arg0) 
			{
			}

			@Override
			public void onChannelDescriptionChanged(ChannelDescriptionEditedEvent arg0) 
			{
			}

			@Override
			public void onChannelEdit(ChannelEditedEvent arg0) 
			{	
			}

			@Override
			public void onChannelMoved(ChannelMovedEvent arg0) 
			{
			}

			@Override
			public void onChannelPasswordChanged(ChannelPasswordChangedEvent arg0) 
			{
			}

			@Override
			public void onClientMoved(ClientMovedEvent arg0) 
			{
			}

			@Override
			public void onServerEdit(ServerEditedEvent arg0) 
			{
			}
		});
	}
}

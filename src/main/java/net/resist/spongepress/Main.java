package net.resist.spongepress;
import com.google.inject.Inject;
import net.resist.spongepress.commands.loginCMD;
import net.resist.spongepress.commands.logoutCMD;
import net.resist.spongepress.commands.setpassCMD;
import net.resist.spongepress.database.DataStoreManager;
import net.resist.spongepress.database.IDataStore;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import org.slf4j.Logger;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandManager;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameAboutToStartServerEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.user.UserStorageService;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.serializer.TextSerializers;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import net.kyori.text.serializer.ComponentSerializers;
import net.kyori.text.TextComponent;
import net.kyori.text.Component;
import net.kyori.text.event.ClickEvent;
import net.kyori.text.event.HoverEvent;
import net.kyori.text.format.TextColor;
import net.kyori.text.format.TextDecoration;

@Plugin(id="resist-spongepress-plugin",name="RESIST SpongePress Plugin",version="1.0.2",description="Wordpress Authentication and Utilities")
public class Main{
	@Inject
	private Logger logger;
	@Inject
	@ConfigDir(sharedRoot=true)
	public Path configDir;
	public Path resistConf;
	public File userFile;
	public Config config;
	public String wordpressToken;
	private DataStoreManager dataStoreManager;
	private CommandManager cmdManager=Sponge.getCommandManager();
	private static final String formType="multipart/form-data; boundary=----WebKitFormBoundary7MA4YWxkTrZu0gW";
	@Listener
	public void Init(GameInitializationEvent event) throws IOException,ObjectMappingException{
		resistConf = configDir.resolve("resist/spongepress.conf");
		Files.createDirectories(resistConf.getParent());
		Sponge.getEventManager().registerListeners(this,new PlayerListener(this));
		config=new Config(this);
		loadCommands();
	}
	@Listener
	public void onServerAboutStart(GameAboutToStartServerEvent event){
		dataStoreManager=new DataStoreManager(this);
		if(dataStoreManager.load()){
			getLogger().info("Database is Loading...");
			getDataStore().clearList();
		}else{
			getLogger().error("Something is wrong with your config, disabling plugin!");
		}
	}
	@Listener
	public void onServerStart(GameStartedServerEvent event) throws IOException{
		try{
			MediaType mediaType=MediaType.parse(formType);
			OkHttpClient client=new OkHttpClient();
			RequestBody bodyGetToken=RequestBody.create(mediaType,
				"------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"username\"\r\n\r\n"
					+Config.wordpressAdminUser
					+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\n"
					+Config.wordpressAdminPass+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--");
			Request requestGetToken=new Request.Builder().url(Config.wordpressURL+"/wp-json/jwt-auth/v1/token").post(
				bodyGetToken).addHeader("content-type",formType).addHeader("cache-control","no-cache").build();
			Response responseGetToken=client.newCall(requestGetToken).execute();
			String tokenString=responseGetToken.body().string();
			List<String> list=new ArrayList<>(Arrays.asList(tokenString.split("\":\"")));
			wordpressToken=list.get(1).replace("\",\"user_email","");
			logger.info("Wordpress token was created successfully!");
		}catch(Exception e){
			getLogger().error("Wordpress Token Error: "+e);
		}
		logger.info("Plugin Loaded Successfully!");
	}
	@Listener
	public void onPluginReload(GameReloadEvent event) throws IOException,ObjectMappingException{
		this.config=new Config(this);
		loadDataStore();
	}
	private void loadCommands(){
		//Removed since Resist is ONLINE mode only. :P Pay for Minecraft!
		//CommandSpec login=CommandSpec.builder().description(Text.of("Login to the Server.")).arguments(GenericArguments
			//.string(Text.of("password"))).executor(new loginCMD(this)).build();
		//cmdManager.register(this,login,"login");
		//CommandSpec logout=CommandSpec.builder().description(Text.of("Logout of the Server.")).arguments().executor(
			//new logoutCMD(this)).build();
		//cmdManager.register(this,logout,"logout");
		CommandSpec setpass=CommandSpec.builder().description(Text.of("Set Password.")).arguments(
			GenericArguments.string(Text.of("password")),GenericArguments.string(Text.of("passwordAgain"))).executor(
				new setpassCMD(this)).build();
		cmdManager.register(this,setpass,"setpass");
	}
	public void loadDataStore(){
		if(dataStoreManager.load()){
			getLogger().info("Database Loaded!");
		}else{
			getLogger().error("Unable to load a database please check your Console/Config!");
		}
	}
	public IDataStore getDataStore(){
		return dataStoreManager.getDataStore();
	}
	@Listener
	public void onPlayerLogin(ClientConnectionEvent.Join event,@Root Player player){
		String playerName=player.getName();
		if(!getDataStore().getAccepted().contains(player.getName())){
			getLogger().info("Player "+playerName+" is a new player to the server!");
			//sendMessage(player,Config.chatPrefix+"Welcome to the server &l&e"+playerName+"&r!");
			try{
				Random rnd=new Random();
				int number=rnd.nextInt(999999);
				String newPass=String.format("%06d",number);
				OkHttpClient client=new OkHttpClient();
				MediaType mediaType=MediaType.parse(formType);
				RequestBody body=RequestBody.create(mediaType,
					"------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"username\"\r\n\r\n"
						+playerName
						+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"email\"\r\n\r\n"
						+playerName+"@"+Config.wordpressNewUserEmailDomain
						+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW\r\nContent-Disposition: form-data; name=\"password\"\r\n\r\n"
						+newPass+"\r\n------WebKitFormBoundary7MA4YWxkTrZu0gW--");
				Request request=new Request.Builder().url("https://resist.network/wp-json/wp/v2/users").post(body)
					.addHeader("content-type",formType).addHeader("authorization","Bearer "+wordpressToken).addHeader(
						"cache-control","no-cache").build();
				@SuppressWarnings("unused")
				Response response=client.newCall(request).execute();
				sendMessage(player,Config.chatPrefix+"Your new forum password is: &l&b"+newPass+"&r");
				sendMessage(player,Config.chatPrefix+"Use &l&b/setpass&r to change at anytime!");
				getDataStore().addPlayer(player.getName());
			}catch(Exception e){
				getLogger().error("Create User Error: "+e);
			}
		}else{
			//We had a login here, but Resist is ONLINE mode only, so removing. 
			//https://github.com/resist-network/resist-spongepress-plugin commit history will have this information. 
			//sendMessage(player,Config.chatPrefix+"Welcome back &l&e"+playerName+"&r!");
			sendMessage(player,Config.chatPrefix+"&7Your forum password is already set.");
			//sendMessage(player,Config.chatPrefix+""+Config.profileURL+"/"+playerName);			
			//TextComponent profileComponent;
			//profileComponent = TextComponent.of("Hello ")
			//  .color(TextColor.GOLD)
			//  .append(
			//	TextComponent.of("world")
			//	  .color(TextColor.AQUA).
			//	  decoration(TextDecoration.BOLD, true)
			//  )
			//  .append(TextComponent.of("!").color(TextColor.RED));
			//profileComponent = profileComponent.toBuilder().applyDeep(c -> {
			//	c.clickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tps"));
			//	c.hoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.of("Click to View Your Profile!").color(TextColor.AQUA)));
			//}).build();
			//String profileLink = Config.profileURL;
			//sendMessage(player,Config.chatPrefix+""+profileComponent);
			sendMessage(player,Config.chatPrefix+"&7Use &l&e/setpass&r&7 if you forgot it!");
		}
	}
	@Listener
	public void onPlayerDisconnect(ClientConnectionEvent.Disconnect event,@Root Player player){
		String playerName=player.getName();
		getDataStore().removePlayer(playerName);
	}
	public Logger getLogger(){
		return logger;
	}
	public Optional<User> getUser(UUID uuid){
		Optional<UserStorageService> userStorage=Sponge.getServiceManager().provide(UserStorageService.class);
		return userStorage.get().get(uuid);
	}
	public void sendMessage(CommandSource sender,String message){
		sender.sendMessage(fromLegacy(message));
	}
	public Text fromLegacy(String legacy){
		return TextSerializers.FORMATTING_CODE.deserializeUnchecked(legacy);
	}
}
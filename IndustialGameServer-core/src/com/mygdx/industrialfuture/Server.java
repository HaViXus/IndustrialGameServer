package com.mygdx.industrialfuture;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.zip.DeflaterOutputStream;

import javax.swing.JOptionPane;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import Packets.ClientGeneratingPacket;
import Packets.ClientWaitingPacket;
import Packets.ServerMapPacket;
import Packets.ServerGeneratingPacket;
import Packets.ServerWaitingPacket;
import bytepacket.ByteConverter;
import client_server_interface.Server_info;
import map_generator.MapGenerator;
import map_generator.MapTile;



public class Server extends ApplicationAdapter
{
	Socket socket;
	ServerSocket serverSocket;
	InetSocketAddress address;
	
	int clients;
	int maxPlayers;
	
	ArrayList<Boolean> playersSlots;
	long[] playersSendTimes; 
	ArrayList<Integer> playersErrors;
	boolean[] playersNewData;
	boolean[] isPlayerReady;
	
	final int MAX_ERRORS = 10;
	float waitingRoomReadyTimer;
	
	boolean isGenerating, isGenerated;
	boolean[] playerGetMapData;
	boolean[] isSendedMapToPlayer;
	boolean[] isMapReady;
	
	boolean startGame;
	boolean startInfoReceived[];
	
	MapGenerator generator;
	private float fMap[][];
	private float fTemperature[][];
	private float fHumidity[][];
	private MapTile map[][];
	
	ByteConverter byteConverter;
	
	Server_info.mode server_mode;
	Server_info.status server_status;
	Server_info.waiting_room_status waiting_room_status;
	
	int mapWidth, mapHeight;
	


	public void create (int port, int maxPlayers) {
	
	//	playersPos = new ArrayList<Vector2>();
		playersSlots = new ArrayList<Boolean>();
		playersSendTimes = new long[maxPlayers];
		playersErrors = new ArrayList<Integer>();
		playersNewData = new boolean[maxPlayers];
		isPlayerReady = new boolean[maxPlayers];
		playerGetMapData = new boolean[maxPlayers];
		isSendedMapToPlayer = new boolean[maxPlayers];
		isMapReady = new boolean[maxPlayers];
		
		isGenerating = false;
		isGenerated = false;
		
		clients = 0;
		this.maxPlayers = maxPlayers; 
		startGame = false;
		
		waitingRoomReadyTimer = 0;
		
		server_mode = server_mode.WAITING_ROOM;
		server_status = server_status.EMPTY;
		waiting_room_status = waiting_room_status.NOT_READY;
		
		
		
		for(int i=0;i<maxPlayers;i++)
		{
			playersSlots.add(false);
			playersSendTimes[i] = 0;
			playersErrors.add(0);
			playersNewData[i] = false;
			isPlayerReady[i] = false;
			playerGetMapData[i] = false;
			isSendedMapToPlayer[i] = false;
			isMapReady[i] = false;
		}
		
		mapWidth = 128;
		mapHeight = 128;
	//	fMap = new float[mapHeight][mapWidth];
		generator = new MapGenerator(mapWidth, mapHeight, 0);
		
		byteConverter = new ByteConverter();

		try 
		{
			System.out.println("Waiting: ");
			serverSocket = new ServerSocket(port);
			createServerSocketAcceptLoop();

		} 
		catch (Exception e) { System.out.println("Server: " + e); }
	}	

	
	public void render () {
		
		
		if(server_mode == server_mode.WAITING_ROOM)	
			waitingRoomLoop();
		else if(server_mode == server_mode.GENERATING)
			generatingLoop();
				
	}
	
	
	public void waitingRoomLoop()
	{
		boolean isServerReady = true;
		
		if(clients < 1)
			isServerReady = false;
		else
		{
			for(int i=0; i<clients;i++)					
				if(isPlayerReady[i] == false)
				{
					isServerReady = false;
					break;
				}						
		}
		
		if(isServerReady == true)
		{
			waiting_room_status = waiting_room_status.READY;
			waitingRoomReadyTimer += Gdx.graphics.getDeltaTime();
			if(waitingRoomReadyTimer >= 5.0f)
				server_mode = server_mode.GENERATING;
		}		
		else
			waiting_room_status = waiting_room_status.NOT_READY;
		
		if(clients < 1) server_status = server_status.EMPTY;
		else if(clients >= maxPlayers) server_status = server_status.FULL;
		else server_status = server_status.NOT_EMPTY;
		
	}
	
	public void generatingLoop()
	{
		
		if(isGenerated == false)
		{
			//Tu jakis dodatkowy watek TO DO
			isGenerating = true;
		//	fMap = generator.generateRandomPoints(fMap);
		//	fMap = generator.generatePerlinNoise(fMap, 1, 0.6f, 1);
			fTemperature = new float [generator.getHeight()][generator.getWidth()];
			fHumidity = new float[generator.getHeight()][generator.getWidth()];
			fMap = new float [generator.getHeight()][generator.getWidth()];
			map = new MapTile[generator.getHeight()][generator.getWidth()];
		
			fMap = generator.generateMap(map, fMap, fTemperature, fHumidity, 128, 128, 1, 1, 0, 1, 1, 20, maxPlayers);
			generator.createTerrainGroups(map);
			
			
			
			isGenerating=false;
			isGenerated = true;
		}
		else
		{
			int count = 0;
			for(int i=0;i<maxPlayers;i++)	
				if(isMapReady[i]) count++;
			
			if(count == clients)
			{
				startGame = true;	
				server_mode = server_mode.GAME;
			}
		}
		
		
	}
	
	public synchronized void createServerSocketAcceptLoop() 
	{
		new Thread(new Runnable() {		
			@Override
			public void run() 
			{				
				while(server_mode == server_mode.WAITING_ROOM)
				{
					
					try {
						socket = serverSocket.accept();
						System.out.println("Server: Zg³osi³ siê klient!");
						clients++;						
						int tmpID=-1;						
						
						for(int i=0;i<playersSlots.size();i++) //Looking for slot for new player
						{
							if(playersSlots.get(i) == false) // if slot is empty then add player
							{
								tmpID = i;
								playersSlots.set(tmpID,true);
								playersErrors.set(tmpID, 0);
								playersNewData[i] = true;								
								
								new ServiceSendThread(socket, tmpID).start();
								new ServiceReceiveThread(socket, tmpID).start();	
								
								break;
							}
						}
						if(tmpID == -1) // if server hasn't empty slot then send info to client
						{
							ObjectOutputStream out_sock = new ObjectOutputStream(socket.getOutputStream());
							ServerWaitingPacket waitingPacket = new ServerWaitingPacket();
							
							waitingPacket.server_mode = server_mode;
							waitingPacket.server_status = server_status;	
							waitingPacket.PlayerID = -1;									
										
							out_sock.writeObject(waitingPacket);
							out_sock.reset();	
							
							clients--;
						}
						
					} catch (IOException e) {e.printStackTrace();}					
				}			
			}
		}).start();
	}
	
	public class ServiceReceiveThread extends Thread
	{
		private Socket socket;
		private int myID;
		public ServiceReceiveThread(Socket socket, int ID) 
		{
			this.socket = socket;
			this.myID = ID;
		}
		
		public synchronized void run()
		{
			try
			{										
				
				ObjectInputStream in_sock = new ObjectInputStream(socket.getInputStream());
				ClientWaitingPacket waitingPacket;
				ClientGeneratingPacket generatingPacket;
				

				while(playersErrors.get(myID) <= MAX_ERRORS)
				{
					try
					{
						int packetType = in_sock.readInt();
						
						if( packetType == 1)
						{
							waitingPacket = (ClientWaitingPacket)in_sock.readObject();	
							playersNewData[myID] = true;
							isPlayerReady[myID] = waitingPacket.Ready;
							playersSendTimes[myID] = waitingPacket.SendTime;
							playersErrors.set(myID, 0);
						}
						else if(packetType == 2)
						{
							generatingPacket = (ClientGeneratingPacket)in_sock.readObject();
							playersNewData[myID] = true;
							isSendedMapToPlayer[myID] = generatingPacket.mapReceived;
							playersSendTimes[myID] = generatingPacket.sendTime;
							isMapReady[myID] = generatingPacket.mapReady;
							playersErrors.set(myID, 0);
						}
						
						

					}catch(Exception e) {playersErrors.set( myID, playersErrors.get(myID) + 1 );};
					try
					{
						Thread.currentThread();
						Thread.sleep(5);
					} 
					catch (InterruptedException e) {}
				}
										
				clients--;
				playersSlots.set(myID, false);
				System.out.println("Player nr: " + myID + " is Disconnected");
				socket.close();
			}
			catch (IOException e) { //e.printStackTrace();
			};
			
		}
	}
	
	public class ServiceSendThread extends Thread
	{
		private Socket socket;
		private int myID;
		
		public ServiceSendThread(Socket socket, int ID) 
		{
			this.socket = socket;
			myID = ID;
		}
		
		public synchronized void run()
		{
			try
			{					

				ObjectOutputStream out_sock = new ObjectOutputStream(socket.getOutputStream());		
				ServerWaitingPacket waitingPacket = new ServerWaitingPacket();
				ServerMapPacket mapPacket = new ServerMapPacket();
				ServerGeneratingPacket generatingPacket = new ServerGeneratingPacket();
				
				while(playersErrors.get(myID) <= MAX_ERRORS)
				{
					try
					{
						if(server_mode == server_mode.WAITING_ROOM)
						{
							if(playersNewData[myID] == true || System.currentTimeMillis() - playersSendTimes[myID] >= 100 )
							{
								playersNewData[myID] = false;
								
								waitingPacket.PlayerID = myID;
								waitingPacket.PlayerNumber = clients;
								waitingPacket.MaxPlayers = maxPlayers;
								waitingPacket.SendTime = playersSendTimes[myID];
								waitingPacket.server_mode = server_mode;
								waitingPacket.server_status = server_status;
								waitingPacket.waiting_room_status = waiting_room_status;
							
								
								out_sock.writeInt(1);
								out_sock.reset();
			
								out_sock.writeObject(waitingPacket);
								out_sock.flush();
								out_sock.reset();
								
							}				
						}
						else if(server_mode == server_mode.GENERATING)
						{
							
							if(isSendedMapToPlayer[myID] == false && isGenerated == true)
							{
								
								mapPacket.map = fMap;
								mapPacket.temperature = fTemperature;
								mapPacket.humadity = fHumidity;
								mapPacket.tileMap = map;
								
								out_sock.writeInt(2);
								out_sock.reset();
								
								out_sock.writeInt(fMap.length); //send map height
								out_sock.writeInt(fMap[0].length); //send map width
								out_sock.reset();
								
								out_sock.writeObject(mapPacket);							
								out_sock.flush();
								out_sock.reset();
								
								isSendedMapToPlayer[myID] = true;					
						
							}
							
							if(playersNewData[myID] == true || System.currentTimeMillis() - playersSendTimes[myID] >= 100)
							{
								playersNewData[myID] = false;
										
								generatingPacket.sendTime = playersSendTimes[myID];							
								generatingPacket.playersReady = isMapReady;
								
								out_sock.writeInt(3);
								out_sock.reset();
								
								out_sock.writeObject(generatingPacket);							
								out_sock.flush();
								out_sock.reset();
							}
							
						}
						else if(server_mode == server_mode.GAME)
						{
							
						}
								
						playersErrors.set(myID, 0);
					}catch(Exception e) {playersErrors.set( myID, playersErrors.get(myID) + 1 );};
					try {
						Thread.currentThread();
						Thread.sleep(5);
						} 
					catch (InterruptedException e) {}; 
				}												
			}
			catch (IOException e) { e.printStackTrace();};
			
		}
	}
	

	
	@Override
	public void dispose() {
		try {serverSocket.close();} 
		catch (IOException e) {e.printStackTrace();}
		
		super.dispose();
	}
}

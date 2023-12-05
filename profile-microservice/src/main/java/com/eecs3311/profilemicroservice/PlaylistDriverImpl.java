package com.eecs3311.profilemicroservice;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.*;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	OkHttpClient client = new OkHttpClient();

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Playlist constraint already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {

		return null;
	}

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		
		return null;
	}

	@Override
	public DbQueryStatus generateMixedPlaylist(String userName, String friendUserName) {
		ArrayList<Object> userSongs = new ArrayList<>();
		ArrayList<Object> friendSongs = new ArrayList<>();
		DbQueryStatus status = new DbQueryStatus("", DbQueryExecResult.QUERY_OK);
		try {
			String userQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", userName);
			StatementResult userPlaylist = driver.session().run(userQuery);
			// Gets all the user's songs
			while (userPlaylist.hasNext()) {
				Record record = userPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				userSongs.add(map.get("songId"));
			}
			// Gets all the user's friend's songs.
			String friendQuery = String.format("MATCH (:playlist {plName: '%s-favorites'})-[:includes]->(s:song) RETURN s", friendUserName);
			StatementResult friendPlaylist = driver.session().run(friendQuery);
			while (friendPlaylist.hasNext()) {
				Record record = friendPlaylist.next();
				Map<String, Object> map = record.get(0).asMap();
				friendSongs.add(map.get("songId"));
			}
			userSongs.addAll(friendSongs);
			Collections.shuffle(userSongs);
		} catch (Exception e){
			e.printStackTrace();
			return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}

		// Gets all the song titles from the two playlists
		ArrayList<String> songTitles = new ArrayList<>();
		for(Object id : userSongs){
			String songUrl = "http://localhost:3001/getSongTitleById/" + id;
			Request request = new Request.Builder().url(songUrl).build();
			try {
				Response response = client.newCall(request).execute();
				if (!response.isSuccessful()) return new DbQueryStatus("Error with driver session", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
				JSONObject json = new JSONObject(Objects.requireNonNull(response.body()).string());
				songTitles.add((String) json.get("data"));
			} catch (Exception e) {
				e.printStackTrace();
				return new DbQueryStatus("Error requesting song title with ID: " + id, DbQueryExecResult.QUERY_ERROR_GENERIC);
			}
		}
		status.setData(songTitles);
		status.setMessage("Success");
		return status;
	}
}

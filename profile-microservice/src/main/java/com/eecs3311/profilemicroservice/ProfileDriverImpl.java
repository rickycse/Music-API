package com.eecs3311.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.*;

import org.springframework.stereotype.Repository;
import org.neo4j.driver.v1.Transaction;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			} catch (Exception e) {
				if (e.getMessage().contains("An equivalent constraint already exists")) {
					System.out.println("INFO: Profile constraints already exist (DB likely already initialized), should be OK to continue");
				} else {
					// something else, yuck, bye
					throw e;
				}
			}
			session.close();
		}
	}
	
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		String usernamePlaylist = userName + "-favorites";
		// Start a session
		try (Session session = driver.session()) {

			// Run a query to add profile to Profile database
			String query1 = String.format("CREATE (:profile {userName: '%s', fullName: '%s', password: '%s'})-[:created]->(:playlist {plName: '%s'})", userName, fullName, password, usernamePlaylist);
			session.run(query1);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		// Start a session
		try (Session session = driver.session()) {
			// follow friend query
			String query1 = String.format("MATCH (p1:profile {userName: '%s'}), (p2:profile {userName: '%s'})\nCREATE (p1)-[:follows]->(p2)", userName, frndUserName);
			session.run(query1);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		try (Session session = driver.session()) {
			// unfollow friend query
			String query1 = String.format("MATCH (p1:profile {userName: '%s'})-[r:follows]->(p2:profile {userName: '%s'}) DELETE r", userName, frndUserName);
			session.run(query1);

			return new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}

	public String[] getStringArrayFromArrayList(List<String> list) {
		String[] arr = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			arr[i] = list.get(i);
		}
		return arr;
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		// Start a session
		try (Session session = driver.session()) {
			// creating variables
			Map<String, String[]> userNameToLikedSongs = new HashMap<>();
			String[] emptyArrayTemp = new String[0];

			// Performing query1 to retrieve a list of all profile(s) userName follows
			String query1 = String.format("MATCH (p:profile {userName: '%s'})-[:follows]->(friend:profile) RETURN friend.userName AS userName", userName);
			StatementResult result = session.run(query1);

			// making sure retrieved result is not empty (aka userName doe not follow anyone)
			if (result.hasNext()) {
				// creating an array to store all usernames of friends
				List<String> friendList = new ArrayList<>();

				// looping through all exiting results
				while (result.hasNext()) {
					Record record = result.next();

					// store all record values in 'friendList' string array
					friendList.add(record.get("userName").asString());
				}

				// Loop through each index in 'friendList' and determine if there are any liked songs to return
				int index = 0;
				String currentUserName = String.valueOf(friendList.get(index));
				String userFavPlaylistName = currentUserName + "-favorites";

				while (index < friendList.size()) {
					// setting up query2 to use friend's userName to get their liked songs
					String query2 = String.format("MATCH (p:profile {userName: '%s'})-[:created]->(pl:playlist {plName: '%s'})-[:includes]->(song:song)\nRETURN song.songId AS likedSongId", currentUserName, userFavPlaylistName);
					StatementResult result2 = driver.session().run(query2);

					// Check to see if there are any liked songs for current friend in the list
					if (result2.hasNext()) {
						List<String> likedSongIds = new ArrayList<>();

						// loop through the result2 values, and store it into the map corresponding to the userName
						while (result2.hasNext()) {
							Record record2 = result2.next();

							// store all record2 values in 'likedSongIds' string array
							System.out.println("SONG: "+record2.toString());
							likedSongIds.add(record2.get("likedSongId").asString());
						}

						// Storing friendName  and likedSongIds into map
						Object[] friendArr = likedSongIds.toArray();
						userNameToLikedSongs.put(String.valueOf(friendList.get(index)), getStringArrayFromArrayList(likedSongIds));

						// After storing likedSongIds into the map, then we will delete all the data from the likedSongIds
						likedSongIds.clear();
					} else {
						// Storing currentFriendUserName and the empty array as the likedSongsIds in the map
						userNameToLikedSongs.put(String.valueOf(friendList.get(index)), emptyArrayTemp);
					}

					// Before the end of the loop, increment index and change currentUserName
					index++;
					if (index < friendList.size()) {
						// there's still more friends
						currentUserName = String.valueOf(friendList.get(index));
					}

				}

				DbQueryStatus dbQueryStat = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
				dbQueryStat.setData(userNameToLikedSongs);
				return dbQueryStat;
			} else {
				// user doe not follow any friends, return empty map and HTTP OK response
				DbQueryStatus dbQueryStat = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
				dbQueryStat.setData(userNameToLikedSongs);
				return dbQueryStat;
			}
		} catch (Exception e) {
			System.err.println("Error: " + e.getMessage());
			return new DbQueryStatus("Failed: " + e.getMessage(), DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
	}
}

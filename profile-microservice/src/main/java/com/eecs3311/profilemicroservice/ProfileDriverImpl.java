package com.eecs3311.profilemicroservice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

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
		DbQueryStatus status;
		try {
			Session session = driver.session();
			session.run(
					String.format("CREATE (:profile {userName: '%s', fullName: '%s', password: '%s'})", fullName, password, userName)
			);
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus followFriend(String userName, String friendUserName) {
		DbQueryStatus status;
		try {
			Session session = driver.session();
			session.run(
					String.format("MATCH (p:profile {userName: '%s'}), (p1:profile {userName: '%s'}) CREATE (p)-[:follows]->(p1)", userName, friendUserName)
			);
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String friendUserName) {
		DbQueryStatus status;
		try {
			Session session = driver.session();
			session.run(
					String.format("MATCH (p:profile {userName: '%s'})-[r:follows]->(p1:profile {userName: '%s'}) DELETE r", userName, friendUserName)
			);
			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
		}
		return status;
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		DbQueryStatus status;
		StatementResult result;
		try {
			Session session = driver.session();
			result = session.run(String.format("MATCH (p:profile {userName: '%s'})-[r:liked]->(s:song) return r", userName));

			status = new DbQueryStatus("Success", DbQueryExecResult.QUERY_OK);
			status.setData(result);
		} catch (Exception e){
			status = new DbQueryStatus("Failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
			status.setData(null);
		}
		return status;
	}
}

package io.seeport.rachlin.aismessagelistener.jdbc.dBinserter;


import org.postgresql.geometric.PGpoint;

import io.seeport.rachlin.aismessagelistener.jdbc.DatabaseConnectionInterface;
import io.seeport.rachlin.aismessagelistener.jdbc.messageData.MessageDataInterface;
import io.seeport.rachlin.aismessagelistener.jdbc.util.CoordinateUtil;



import java.sql.SQLException;
import java.time.OffsetDateTime;

import dk.tbsalling.aismessages.ais.exceptions.UnsupportedMessageType;

/**
 * Insertion logic that can be abstracted out of individual message types live in this Abstract
 * object. DatabaseInserter objects are to be constructed with minimally a MessageDataInterface
 * implementation, i.e our wrappers around supported message functionality. DatabaseInserters must
 * also have a connection to the database attached in order to commit write transactions.
 */
public abstract class AbstractDatabaseInserter implements DatabaseInserterInterface {

  protected MessageDataInterface message;
  protected DatabaseConnectionInterface connection;

  private int vesselSignaturePrimaryKey;
  private int navigationDataPrimaryKey;
  private int voyageDataPrimaryKey;
  private int vesselDataPrimaryKey;
  private int geospatialDataPrimaryKey;

  /**
   * Constructor that supplies a MessageData implementation wrapper and an existing Database
   * connection to be attached.
   */
  protected AbstractDatabaseInserter(MessageDataInterface message, DatabaseConnectionInterface connection) {
    this(message);
    attachConnection(connection);
  }

  /**
   * Default constructor supplies a MessageData implementation wrapper to be inserted to the
   * database.
   */
  protected AbstractDatabaseInserter(MessageDataInterface message) {
    this.message = message;

    // -1 Indicates that a pk has not been created for this message.
    vesselSignaturePrimaryKey = -1;
    navigationDataPrimaryKey = -1;
    voyageDataPrimaryKey = -1;
    vesselDataPrimaryKey = -1;
    geospatialDataPrimaryKey = -1;
  }

  @Override
  public void attachConnection(DatabaseConnectionInterface conn) {
    this.connection = conn;
  }

  @Override
  public WriteResult writeMessage() throws SQLException {
    connection.connectIfDropped();
    connection.beginTransaction();

    try {
      writeVesselData();
      writeVesselSignature();
      writeVoyageData();
      writeNavigationData();
      writeGeospatialData();
      writeMessageData();

      connection.commitTransaction();
      return WriteResult.SUCCESS;
    } catch (SQLException ex) {
      ex.printStackTrace();
      connection.rollBackTransaction();
    }
    return WriteResult.FAILURE;
  }

  @Override
  public WriteResult writeMessageData() throws SQLException {
    try {
      OffsetDateTime timeReceived = message.getTimeReceived();
      Boolean isValidMessage = message.isValidType();
      Boolean isMultiPart = message.hasMultipleParts();
      String rawNMEA = message.getRawNMEA();
      int messageTypeId = message.getMessageTypeId();
      String geoDataKey = geospatialDataPrimaryKey == -1 ? "NULL" : Integer.toString(geospatialDataPrimaryKey);
      String navDataKey = navigationDataPrimaryKey == -1 ? "NULL" : Integer.toString(navigationDataPrimaryKey);
      String voyageDataKey = voyageDataPrimaryKey == -1 ? "NULL" : Integer.toString(voyageDataPrimaryKey);
      String vesselDataKey = vesselDataPrimaryKey == -1 ? "NULL" : Integer.toString(vesselDataPrimaryKey);
      if (vesselSignaturePrimaryKey == -1) {
        System.err.println("Cannot update message_data table without first updating vessel_signature.\n");
      }

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO message_data(" +
                      "time_received," +
                      "is_valid_msg," +
                      "is_multi_part," +
                      "raw_nmea," +
                      "message_type_id," +
                      "geospatial_data_id," +
                      "navigation_data_id," +
                      "voyage_data_id," +
                      "vessel_signature_id," +
                      "vessel_data_id" +
                      ") " +
                      "VALUES ('" +
                      timeReceived + "'," +
                      isValidMessage + "," +
                      isMultiPart + ",'" +
                      rawNMEA + "'," +
                      messageTypeId + "," +
                      geoDataKey + "," +
                      navDataKey + "," +
                      voyageDataKey + "," +
                      vesselSignaturePrimaryKey + "," +
                      vesselDataKey +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for msg_data record.\n");
      }

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // None of these methods should throw an UnsupportedMessageType exception.
      System.err.println("Could not access data needed for writing to message_data table.\n");
      System.exit(1);


      return WriteResult.UNSUPPORTED;
    }
  }

  @Override
  public WriteResult writeVesselSignature() throws SQLException {
    try {
      String imo;
      if (message.getIMO().isPresent()) {
        imo = "" + message.getIMO().get();
      } else {
        imo = "NULL";
      }

      String vesselTypeId;
      if (message.getVesselTypeId().isPresent()) {
        vesselTypeId = "" + message.getVesselTypeId().get();
      } else {
        vesselTypeId = "NULL";
      }

      int mmsi = message.getMMSI();
      String callSign = message.getCallsign();
      String name = message.getShipName();

      // Check if vessel signature already exists in table.
      //TODO: getVesselSignatureIdFromFullyQualifiedSignature may not handle null values correctly.
      //      Currently a non-issue, as we have not encountered a fully qualified signature that
      //      contains nulls.
      int vesselSignatureID = connection.getVesselSignatureIdFromFullyQualifiedSignature(mmsi, imo, callSign, name, vesselTypeId);
      if (vesselSignatureID != -1) {
        this.vesselSignaturePrimaryKey = vesselSignatureID;
        return WriteResult.SUCCESS;
      }

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO vessel_signature(" +
                      "mmsi," +
                      "imo," +
                      "call_sign," +
                      "name," +
                      "vessel_type_id" +
                      ") " +
                      "VALUES (" +
                      mmsi + "," +
                      imo + ",'" +
                      callSign + "','" +
                      name + "'," +
                      vesselTypeId +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for vessel_signature record.\n");
      }
      this.vesselSignaturePrimaryKey = primaryKey; // Update for writeMessageData foreign key.
      return WriteResult.SUCCESS;

    } catch (UnsupportedMessageType ex) {

      // Check if vessel signature already exists in table.
      int vesselSignatureID = connection.getVesselSignatureIdWithMMSI(message.getMMSI());
      if (vesselSignatureID != -1) {
        this.vesselSignaturePrimaryKey = vesselSignatureID;
        return WriteResult.SUCCESS;
      }

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO vessel_signature(" +
                      "mmsi," +
                      "imo," +
                      "call_sign," +
                      "name," +
                      "vessel_type_id" +
                      ") " +
                      "VALUES (" +
                      message.getMMSI() + "," +
                      "NULL," +
                      "NULL," +
                      "NULL," +
                      "NULL" +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for MMSI-only vessel_signature record.\n");
      }
      this.vesselSignaturePrimaryKey = primaryKey;
      return WriteResult.SUCCESS;
    }
  }

  @Override
  public WriteResult writeVoyageData() throws SQLException {
    try {
      Float draught = message.getDraught();
      String destination = message.getDestination();

      // TODO: Replace with Prepared Statements
      String sqlUpdate;
      if (message.getETA().isPresent()) {
        sqlUpdate =
                "INSERT INTO voyage_data(" +
                        "draught," +
                        "eta," +
                        "destination" +
                        ") " +
                        "VALUES (" +
                        draught + ",'" +
                        message.getETA().get() + "','" +
                        destination +
                        "')";
      } else {
        sqlUpdate =
                "INSERT INTO voyage_data(" +
                        "draught," +
                        "eta," +
                        "destination" +
                        ") " +
                        "VALUES (" +
                        draught + "," +
                        "NULL" + ",'" +
                        destination +
                        "')";
      }

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for voyage_data record.\n");
      }
      this.voyageDataPrimaryKey = primaryKey; // Update for writeMessageData foreign key.

      return WriteResult.SUCCESS;
    } catch (
            UnsupportedMessageType ex) {
      // This message does not contain voyage data. FK will be null in Message_Data table.
      return WriteResult.UNSUPPORTED;
    }

  }

  @Override
  public WriteResult writeVesselData() throws SQLException {
    try {
      Integer toBow = message.getToBow();
      Integer toStern = message.getToStern();
      Integer toPort = message.getToPort();
      Integer toStarboard = message.getToStarboard();

      // Check if vessel data already exists in table.
      int vesselDataID = connection.getVesselDataIdFromRecord(toBow, toStern, toPort, toStarboard);
      if (vesselDataID != -1) {
        this.vesselDataPrimaryKey = vesselDataID;
        return WriteResult.SUCCESS;
      }

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO vessel_data(" +
                      "to_bow," +
                      "to_stern," +
                      "to_port," +
                      "to_starboard" +
                      ") " +
                      "VALUES (" +
                      toBow + "," +
                      toStern + "," +
                      toPort + "," +
                      toStarboard +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for vessel_data record.\n");
      }
      this.vesselDataPrimaryKey = primaryKey; // Update for writeMessageData foreign key.
      return WriteResult.SUCCESS;

    } catch (UnsupportedMessageType ex) {
      // This message does not contain vessel data. FK will be null in Message_Data table.
      return WriteResult.UNSUPPORTED;
    }
  }

  @Override
  public WriteResult writeNavigationData() throws SQLException {
    try {

      Float sog = message.getSpeedOverGround();
      Float cog = message.getCourseOverGround();
      Float heading = message.getHeading();
      Float rot = message.getRateOfTurn();
      int navStatusId = message.getNavStatusId();
      int maneuverIndicatorId = message.getManeuverIndicatorId();

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO navigation_data(" +
                      "speed_over_ground," +
                      "course_over_ground," +
                      "heading," +
                      "rate_of_turn," +
                      "nav_status_id," +
                      "maneuver_indicator_id" +
                      ") " +
                      "VALUES (" +
                      sog + "," +
                      cog + "," +
                      heading + "," +
                      rot + "," +
                      navStatusId + "," +
                      maneuverIndicatorId +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for navigation_data record.\n");
      }
      this.navigationDataPrimaryKey = primaryKey; // Update for writeMessageData foreign key.

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // This message does not contain navigation data. FK will be null in Message_Data table.
      return WriteResult.UNSUPPORTED;
    }
  }

  @Override
  public WriteResult writeGeospatialData() throws SQLException {
    try {
      int accuracy = message.getAccuracy() ? 1 : 0;
      // TODO: use appropriate geography data type for PostGIS extension
      PGpoint coord = CoordinateUtil.getCoord(message.getLat(), message.getLong());

      // TODO: Replace with Prepared Statement
      String sqlUpdate =
              "INSERT INTO geospatial_data(" +
                      "coord," +
                      "accuracy" +
                      ") " +
                      "VALUES ('" +
                      coord + "'," +
                      accuracy +
                      ")";

      int primaryKey = connection.insertOneRecord(sqlUpdate);
      if (primaryKey == -1) {
        throw new SQLException("Error recording primary key for geospatial_data record.\n");
      }
      this.geospatialDataPrimaryKey = primaryKey; // Update for writeMessageData foreign key.

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // This message does not contain geospatial data. FK will be null in Message_Data table.
      return WriteResult.UNSUPPORTED;
    }
  }

  // NOTE: vessel_type table is a static reference table
  @Deprecated
  @Override
  public WriteResult writeVesselType() throws SQLException {
    try {
      message.getVesselTypeId();

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // WRITE BLANK RECORD WITH NULL COLUMNS
      return WriteResult.UNSUPPORTED;
    } catch (Exception ex) {
      return WriteResult.FAILURE;
    }
  }

  // NOTE: nav_status table is a static reference table
  @Deprecated
  @Override
  public WriteResult writeNavigationStatus() throws SQLException {
    try {

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // WRITE BLANK RECORD WITH NULL COLUMNS
      return WriteResult.UNSUPPORTED;
    }
  }

  // NOTE: maneuver_indicator table is a static reference table
  @Deprecated
  @Override
  public WriteResult writeManeuverIndicator() throws SQLException {
    try {
      message.getManeuverIndicatorId();

      return WriteResult.SUCCESS;
    } catch (UnsupportedMessageType ex) {
      // WRITE BLANK RECORD WITH NULL COLUMNS
      return WriteResult.UNSUPPORTED;
    } catch (Exception ex) {
      return WriteResult.FAILURE;

    }
  }
}

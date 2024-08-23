# Redis-Based-Chat-Room
Project Overview
This project is a Redis-based messaging system that allows users to create and manage groups, send messages within those groups, and retrieve messages from a specific time frame. The project leverages Redis for efficient data storage and retrieval, implementing a key-value store architecture to manage group messages.

Features
Group Management:

Create Group: Allows the creation of a group with attributes such as name, description, and members.
List Groups: Retrieve a list of all existing groups.
Add Members: Add members to an existing group.
Remove Members: Remove members from a group.
Delete Group: Completely remove a group and its associated data.
Message Management:

Send Message: Send a message to a group with attributes like sender, timestamp, and content.
Retrieve Messages: Retrieve the last 'n' hours of messages from a group.
Publish/Subscribe: Subscribe to a group to receive real-time messages and publish messages to a group.
Technical Details
Redis
The project uses Redis as the primary database, chosen for its in-memory data structure store, which provides high performance and flexibility for managing real-time messaging applications.

Key-Value Design
Keys: The keys are constructed using the format <group name>-<sender name>-<timestamp>, ensuring that messages are uniquely identified and easily retrievable.
Values: The values associated with these keys store the actual message content and metadata.
Pipelining
To enhance performance, especially for bulk operations, the project utilizes Redis pipelining, allowing multiple commands to be sent to Redis without waiting for individual responses, thus reducing latency.

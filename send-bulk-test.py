#!/usr/bin/env python3
"""
Bulk notification test script
Sends messages to multiple phone numbers and email addresses
Site: clapgrow.frappe.cloud
"""

import requests
import json
import time
from typing import List, Dict

API_URL = "http://localhost:8080/api/v1"
# API Key for clapgrow.frappe.cloud site
API_KEY = "Q8S7MnPJSHZlx2Sfz9NqV1mkKCWY188auuVEfyASoh8yCQYMwntUnt3j3Ud7nNErq_XULuYqDGKdMMTVBCy83g"

# Phone numbers
PHONE_NUMBERS = [
    "+918576882906",
    "+918910261784",
    "+917044345461",
    "+919836858910",
    "+917003999234",
    "+918335982196",
    "+919804969862",
]

# Email addresses
EMAIL_ADDRESSES = [
    "sourav@clapgrow.com",
    "souravns1997@gmail.com",
    "souravsingh2609@gmail.com",
    "singh.sourav2609@gmail.com",
]

# Message content
EMAIL_SUBJECT = "Test Bulk Notification"
EMAIL_BODY = "Hello! This is a test bulk email notification. Testing the efficiency of the notification system."
WHATSAPP_BODY = "Hello! This is a test bulk WhatsApp notification. Testing the efficiency of the notification system."


def build_bulk_notifications() -> List[Dict]:
    """Build the list of notifications for bulk sending"""
    notifications = []
    
    # Add WhatsApp notifications
    for phone in PHONE_NUMBERS:
        notifications.append({
            "channel": "WHATSAPP",
            "recipient": phone,
            "body": WHATSAPP_BODY
        })
    
    # Add Email notifications
    for email in EMAIL_ADDRESSES:
        notifications.append({
            "channel": "EMAIL",
            "recipient": email,
            "subject": EMAIL_SUBJECT,
            "body": EMAIL_BODY,
            "isHtml": False
        })
    
    return notifications


def send_bulk_notifications(notifications: List[Dict]) -> Dict:
    """Send bulk notifications via API"""
    url = f"{API_URL}/notifications/send/bulk"
    headers = {
        "Content-Type": "application/json",
        "X-Site-Key": API_KEY
    }
    payload = {
        "notifications": notifications
    }
    
    print(f"Sending {len(notifications)} notifications...")
    response = requests.post(url, json=payload, headers=headers)
    response.raise_for_status()
    return response.json()


def get_message_logs(size: int = 20) -> Dict:
    """Get recent message logs"""
    url = f"{API_URL}/messages/logs"
    headers = {
        "X-Site-Key": API_KEY
    }
    params = {
        "size": size
    }
    
    response = requests.get(url, headers=headers, params=params)
    response.raise_for_status()
    return response.json()


def get_metrics() -> Dict:
    """Get site metrics"""
    url = f"{API_URL}/metrics/site/summary"
    headers = {
        "X-Site-Key": API_KEY
    }
    
    response = requests.get(url, headers=headers)
    response.raise_for_status()
    return response.json()


def main():
    print("=" * 50)
    print("Bulk Notification Test")
    print("=" * 50)
    print()
    print(f"Sending to:")
    print(f"  - {len(PHONE_NUMBERS)} phone numbers")
    print(f"  - {len(EMAIL_ADDRESSES)} email addresses")
    print(f"  - Total: {len(PHONE_NUMBERS) + len(EMAIL_ADDRESSES)} notifications")
    print()
    
    # Build notifications
    notifications = build_bulk_notifications()
    
    # Record start time
    start_time = time.time()
    
    try:
        # Send bulk notifications
        response = send_bulk_notifications(notifications)
        
        # Record end time
        end_time = time.time()
        elapsed_time = end_time - start_time
        
        print("✓ Response received:")
        print(json.dumps(response, indent=2))
        print()
        
        total_queued = response.get("totalQueued", response.get("totalAccepted", 0))
        message_ids = response.get("messageIds", [])
        
        # Extract message IDs from results if available
        if not message_ids and "results" in response:
            message_ids = [r.get("messageId") for r in response.get("results", []) if r.get("messageId")]
        
        print(f"✓ Successfully queued {total_queued} notifications in {elapsed_time:.2f} seconds")
        if total_queued > 0:
            print(f"  Average time per notification: {elapsed_time/total_queued*1000:.2f}ms")
        print()
        
        if message_ids:
            print(f"Message IDs: {', '.join(message_ids[:5])}{'...' if len(message_ids) > 5 else ''}")
            print()
        
        # Wait a bit for processing
        print("Waiting 5 seconds for messages to be processed...")
        time.sleep(5)
        print()
        
        # Check message logs
        print("Checking message logs...")
        logs = get_message_logs(size=50)
        
        messages = logs.get("messages", [])
        total_elements = logs.get("totalElements", 0)
        
        print(f"Total messages in log: {total_elements}")
        print()
        
        # Count by status
        status_counts = {}
        channel_counts = {"EMAIL": 0, "WHATSAPP": 0}
        
        for msg in messages:
            status = msg.get("status", "UNKNOWN")
            channel = msg.get("channel", "UNKNOWN")
            status_counts[status] = status_counts.get(status, 0) + 1
            if channel in channel_counts:
                channel_counts[channel] += 1
        
        print("Status breakdown:")
        for status, count in sorted(status_counts.items()):
            print(f"  {status}: {count}")
        print()
        
        print("Channel breakdown:")
        for channel, count in channel_counts.items():
            print(f"  {channel}: {count}")
        print()
        
        # Show recent messages
        print("Recent messages (first 5):")
        for i, msg in enumerate(messages[:5], 1):
            print(f"  {i}. {msg.get('channel')} to {msg.get('recipient')} - {msg.get('status')}")
        print()
        
        # Get metrics
        print("Fetching metrics...")
        metrics = get_metrics()
        print(json.dumps(metrics, indent=2))
        print()
        
    except requests.exceptions.RequestException as e:
        print(f"✗ Error sending notifications: {e}")
        if hasattr(e, 'response') and e.response is not None:
            print(f"  Response: {e.response.text}")
        return 1
    
    print("=" * 50)
    print("Test completed!")
    print("=" * 50)
    print()
    print("You can check the status of messages at:")
    print(f"  curl -H \"X-Site-Key: {API_KEY}\" {API_URL}/messages/logs")
    print()
    print("Or view metrics at:")
    print(f"  curl -H \"X-Site-Key: {API_KEY}\" {API_URL}/metrics/site/summary")
    
    return 0


if __name__ == "__main__":
    exit(main())


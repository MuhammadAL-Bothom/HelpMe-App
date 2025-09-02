# 🚑 HelpMe App — Android (Java/Kotlin + Firebase + Google Maps)

## 📌 Overview
HelpMe is an Android mobile application that connects **Users** (who request urgent or everyday help) with **Helpers** (who can accept and complete tasks in real-time).  
It features **role-based navigation**, **Google Maps integration**, and **Firebase backend services** to provide a seamless live help-requesting experience.  

The app simulates a **real-world assistance platform**, where Users can post help requests (like directions, deliveries, or personal support) and Helpers nearby can accept and complete them.  

---

## ✨ Key Features

### 🔑 Authentication & Roles
- Firebase **Authentication** for sign up and login.  
- Role-based navigation:  
  - **User:** can create requests.  
  - **Helper:** can view requests and accept/reject them.  

### 📍 Maps & Location
- Google Maps SDK integration.  
- Shows **live user location** with a marker.  
- **Reverse geocoding** to convert coordinates into readable addresses.  
- Handles runtime **location permissions**.  

### 📡 Orders & Firestore Integration
- Order lifecycle with **real-time updates**:  
  - `searching` → request is broadcasted to Helpers.  
  - `accepted` → Helper takes the request.  
  - `in-progress` → Helper is on the way or assisting.  
  - `completed` → Request closed successfully.  
- `deniedBy[]` array in Firestore ensures that Helpers who reject a request won’t be shown the same order again.  

### 🎛 UI & Experience
- Splash screen with branding.  
- Clean **login & registration** pages.  
- Dynamic **maps overlays** for requests and statuses.  
- Modern **Material Design UI** with floating action buttons and cards.  
- Clear status banners for ongoing tasks.  
- Order history & account details pages.  

---

## 📱 Screenshots

### Authentication Flow
<p align="center"> 
  <img src="https://github.com/user-attachments/assets/4aaaf6b2-98b2-4283-a578-641c0eaaea6d" width="240" alt="Splash"/> 
  <img src="https://github.com/user-attachments/assets/966f908d-6746-4838-b329-784d0c4a018c" width="240" alt="Login"/> 
  <img src="https://github.com/user-attachments/assets/a656cc4e-934c-498b-823d-7e300c65a037" width="240" alt="Register"/> 
</p>  

---

### User Journey
<p align="center"> 
  <img src="https://github.com/user-attachments/assets/78fb387a-790c-4e16-a549-6e7811325852" width="240" alt="User Map"/> 
  <img src="https://github.com/user-attachments/assets/e6e761e5-fc7f-4f5a-8646-4bc2e61c758c" width="240" alt="Request Sent"/> 
  <img src="https://github.com/user-attachments/assets/daa1f60a-0680-4ddd-9300-71f50b9ec525" width="240" alt="Request Status"/> 
</p>  

---

### Helper Journey
<p align="center"> 
  <img src="https://github.com/user-attachments/assets/4e4039b7-5c22-4163-b99d-c667f6ee2e61" width="240" alt="Helper Inbox"/> 
  <img src="https://github.com/user-attachments/assets/9d4d0724-9ac6-4fb3-aaae-7b8da6858856" width="240" alt="Helper Accept"/> 
  <img src="https://github.com/user-attachments/assets/0195e2f7-570f-48f7-b16b-4d7d2a10363b" width="240" alt="In Progress"/> 
</p>  

---

### Order Management
<p align="center"> 
  <img src="https://github.com/user-attachments/assets/eecbebaa-ffac-45f4-ab06-5d163ff84763" width="240" alt="Completed Order"/> 
  <img src="https://github.com/user-attachments/assets/05dd7e97-f869-4726-bb7e-8a06eb5be418" width="240" alt="Orders List"/> 
  <img src="https://github.com/user-attachments/assets/d4738f39-a9af-40b2-a466-63132f3195c7" width="240" alt="Account"/> 
</p>  

---

### Extra Screens
<p align="center"> 
  <img src="https://github.com/user-attachments/assets/e513f025-a995-433f-9a05-6d63ff8c4d16" width="240" alt="Order Details"/> 
  <img src="https://github.com/user-attachments/assets/9eecf5f4-d925-4d2e-b554-75ac9df6c556" width="240" alt="Settings / Profile"/> 
</p>  

---

## 🛠 Tech Stack

- **Language:** Java / Kotlin  
- **Backend:** Firebase  
  - **Authentication** (email/password)  
  - **Cloud Firestore** (orders, user data)  
- **Maps:** Google Maps SDK + FusedLocationProviderClient  
- **UI:** Material Components, ConstraintLayout, Overlays, FABs  

---

## 📂 Project Structure
- **MainActivity.java** → Role-based navigation.  
- **UserMapActivity.java** → Displays map, live location, send requests.  
- **HelperInboxActivity.java** → Displays nearby requests.  
- **FirestoreOrderService.java** → Handles Firestore CRUD for orders.  
- **AccountActivity.java** → User account info & order history.  

---

## ⚙️ Setup Instructions

1. **Clone the repo** into Android Studio:  
   ```bash
   git clone https://github.com/MuhammadAL-Bothom/HelpMe-App

Firebase Setup

Create a Firebase project.

Enable Authentication (Email/Password).

Enable Cloud Firestore.

Download google-services.json and place it inside /app.

Google Maps Setup

Get a Maps SDK API Key from Google Cloud Console

Add the key to your AndroidManifest.xml:

<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_API_KEY"/>


Build & Run

Connect a device or emulator with Google Play services.

Run the app from Android Studio.

🚀 How It Works

Users

Login or register as a User.

Allow location permission → map centers on current location.

Press button → send a help request (status: searching).

Track request status in real-time until completion.

Helpers

Login or register as a Helper.

View incoming requests (excluding denied ones).

Accept request → status updates to accepted/in-progress.

Complete task → status updates to completed.

⚠️ Notes

Google Maps requires an emulator/device with Google Play Services installed.

Firestore security rules should restrict users to their own data.

Internet connection is required for live updates & Maps SDK.

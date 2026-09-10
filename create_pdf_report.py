import os
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Image, KeepTogether, HRFlowable
)

pdf_path = "c:/Users/Santosh/Downloads/campus-canteen/Campus_Canteen_MessQ_Project_Report.pdf"

doc = SimpleDocTemplate(
    pdf_path,
    pagesize=letter,
    rightMargin=40,
    leftMargin=40,
    topMargin=40,
    bottomMargin=40
)

styles = getSampleStyleSheet()

# Custom styles
title_style = ParagraphStyle(
    'DocTitle',
    parent=styles['Normal'],
    fontName='Helvetica-Bold',
    fontSize=22,
    leading=26,
    textColor=colors.HexColor('#1F2937'),
    spaceAfter=4
)

subtitle_style = ParagraphStyle(
    'DocSubTitle',
    parent=styles['Normal'],
    fontName='Helvetica-Oblique',
    fontSize=11,
    leading=15,
    textColor=colors.HexColor('#D97706'),
    spaceAfter=14
)

h1_style = ParagraphStyle(
    'SectionH1',
    parent=styles['Normal'],
    fontName='Helvetica-Bold',
    fontSize=13,
    leading=17,
    textColor=colors.HexColor('#EA580C'),
    spaceBefore=12,
    spaceAfter=6,
    keepWithNext=True
)

h2_style = ParagraphStyle(
    'SectionH2',
    parent=styles['Normal'],
    fontName='Helvetica-Bold',
    fontSize=11,
    leading=15,
    textColor=colors.HexColor('#1F2937'),
    spaceBefore=8,
    spaceAfter=4,
    keepWithNext=True
)

body_style = ParagraphStyle(
    'BodyDark',
    parent=styles['Normal'],
    fontName='Helvetica',
    fontSize=9,
    leading=13,
    textColor=colors.HexColor('#374151'),
    spaceAfter=6
)

table_header_style = ParagraphStyle(
    'TableHeader',
    parent=styles['Normal'],
    fontName='Helvetica-Bold',
    fontSize=8.5,
    leading=11,
    textColor=colors.HexColor('#1F2937')
)

table_cell_style = ParagraphStyle(
    'TableCell',
    parent=styles['Normal'],
    fontName='Helvetica',
    fontSize=8,
    leading=11,
    textColor=colors.HexColor('#374151')
)

caption_style = ParagraphStyle(
    'Caption',
    parent=styles['Normal'],
    fontName='Helvetica-Oblique',
    fontSize=7.5,
    leading=10,
    textColor=colors.HexColor('#6B7280'),
    alignment=1,
    spaceAfter=10
)

story = []

# Title & Header
story.append(Paragraph("Campus Canteen (MessQ) – Project Report", title_style))
story.append(Paragraph("Comprehensive Technical & Architectural Overview for Stakeholders & Partners", subtitle_style))
story.append(HRFlowable(width="100%", thickness=1, color=colors.HexColor('#E5E7EB'), spaceBefore=0, spaceAfter=10))

# 1. Executive Summary
story.append(Paragraph("1. Executive Summary & Purpose", h1_style))
summary_text = (
    "<b>Campus Canteen (MessQ)</b> is a smart campus food ordering and queue management system designed "
    "specifically to eliminate cafeteria rush, crowded lines, and ordering chaos in colleges and universities. "
    "Students can browse block-wise canteens, inspect real-time crowd levels, place customized orders with dietary choices, "
    "and track order preparation in real time. Kitchen staff receive a dedicated management terminal to accept, prepare, "
    "and dispatch orders with two-factor email OTP security."
)
story.append(Paragraph(summary_text, body_style))

# 2. Technology Stack
story.append(Paragraph("2. Technology Stack & Cloud Infrastructure", h1_style))
tech_rows = [
    [Paragraph("<b>Component</b>", table_header_style), Paragraph("<b>Technology</b>", table_header_style), Paragraph("<b>Key Role & Responsibility</b>", table_header_style)],
    [Paragraph("Mobile Client", table_cell_style), Paragraph("Android · Kotlin 2.x", table_cell_style), Paragraph("Native mobile architecture with Coroutines & StateFlow", table_cell_style)],
    [Paragraph("User Interface", table_cell_style), Paragraph("Jetpack Compose (Material3)", table_cell_style), Paragraph("100% declarative UI with Amber & Orange design system", table_cell_style)],
    [Paragraph("Primary Database", table_cell_style), Paragraph("Supabase (PostgreSQL)", table_cell_style), Paragraph("Relational schema with Row Level Security & Realtime WebSockets", table_cell_style)],
    [Paragraph("Companion Backend", table_cell_style), Paragraph("Firebase Suite", table_cell_style), Paragraph("Firestore offline cache, Google Credentials auth, FCM push alerts", table_cell_style)],
    [Paragraph("Email 2FA Security", table_cell_style), Paragraph("Resend API", table_cell_style), Paragraph("Dispatches automated 6-digit branded HTML verification codes", table_cell_style)],
    [Paragraph("Networking & Images", table_cell_style), Paragraph("Retrofit, OkHttp3, Coil", table_cell_style), Paragraph("REST API calls, network diagnostics, and cached image rendering", table_cell_style)]
]

t_tech = Table(tech_rows, colWidths=[1.3*inch, 1.8*inch, 4.2*inch])
t_tech.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#F3F4F6')),
    ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E5E7EB')),
    ('TOPPADDING', (0, 0), (-1, -1), 3),
    ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
]))
story.append(t_tech)
story.append(Spacer(1, 8))

# Figure 1: Architecture
story.append(Paragraph("System Architecture Diagram", h2_style))
if os.path.exists("scratch_diagrams/architecture_diagram.png"):
    story.append(Image("scratch_diagrams/architecture_diagram.png", width=6.8*inch, height=3.4*inch))
    story.append(Paragraph("Figure 1: Dual Backend Architecture & Client-Server Connectivity", caption_style))

# 3. Roles & Permissions Matrix
story.append(Paragraph("3. System Roles & Access Matrix", h1_style))
role_rows = [
    [Paragraph("<b>Capability / Feature</b>", table_header_style), Paragraph("<b>Student / Customer</b>", table_header_style), Paragraph("<b>Canteen Owner / Staff</b>", table_header_style), Paragraph("<b>Guest Visitor</b>", table_header_style)],
    [Paragraph("Authentication", table_cell_style), Paragraph("Email, Google, Reg. No.", table_cell_style), Paragraph("Email + 6-digit Resend OTP", table_cell_style), Paragraph("Explore mode", table_cell_style)],
    [Paragraph("Menu & Crowd Rush", table_cell_style), Paragraph("Yes (All campus blocks)", table_cell_style), Paragraph("Assigned Canteen only", table_cell_style), Paragraph("Yes (View-only)", table_cell_style)],
    [Paragraph("Dish Customization", table_cell_style), Paragraph("Bread type, roast, addons", table_cell_style), Paragraph("Configure ingredients", table_cell_style), Paragraph("View-only", table_cell_style)],
    [Paragraph("Order Placement", table_cell_style), Paragraph("Yes (Generates Token #TK)", table_cell_style), Paragraph("No", table_cell_style), Paragraph("Requires Sign-In", table_cell_style)],
    [Paragraph("Live Order Tracking", table_cell_style), Paragraph("Yes (4-step visual stepper)", table_cell_style), Paragraph("Full kitchen queue view", table_cell_style), Paragraph("No", table_cell_style)],
    [Paragraph("Kitchen Queue Control", table_cell_style), Paragraph("No", table_cell_style), Paragraph("Accept, Ready, Deliver", table_cell_style), Paragraph("No", table_cell_style)],
    [Paragraph("Menu Item Management", table_cell_style), Paragraph("No", table_cell_style), Paragraph("Add, edit, in/out of stock", table_cell_style), Paragraph("No", table_cell_style)],
    [Paragraph("Open/Close Broadcast", table_cell_style), Paragraph("No", table_cell_style), Paragraph("Yes (Toggle with reason)", table_cell_style), Paragraph("No", table_cell_style)],
    [Paragraph("Revenue Reports", table_cell_style), Paragraph("No", table_cell_style), Paragraph("Daily sales & order metrics", table_cell_style), Paragraph("No", table_cell_style)]
]

t_role = Table(role_rows, colWidths=[1.8*inch, 1.8*inch, 2.3*inch, 1.4*inch])
t_role.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#FEF3C7')),
    ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E5E7EB')),
    ('TOPPADDING', (0, 0), (-1, -1), 3),
    ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
]))
story.append(t_role)
story.append(Spacer(1, 8))

# Figure 2: Workflow
story.append(Paragraph("4. End-to-End Operational Workflow", h1_style))
story.append(Paragraph(
    "Orders follow a strict 4-stage lifecycle ensuring synchronized status updates across both parties:",
    body_style
))
if os.path.exists("scratch_diagrams/workflow_diagram.png"):
    story.append(Image("scratch_diagrams/workflow_diagram.png", width=6.8*inch, height=3.0*inch))
    story.append(Paragraph("Figure 2: Four-Stage Real-Time Ordering and Kitchen Execution Flow", caption_style))

# 5. Screen Inventory
story.append(Paragraph("5. Complete Screen Catalog (27 Screens)", h1_style))
scr_rows = [
    [Paragraph("<b>Category</b>", table_header_style), Paragraph("<b>Screen Name</b>", table_header_style), Paragraph("<b>Key Functions & User Actions</b>", table_header_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("SplashScreen", table_cell_style), Paragraph("Validates session tokens, connectivity, and routes to appropriate entry portal.", table_cell_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("NoInternetScreen", table_cell_style), Paragraph("Offline screen with one-tap connection re-validation.", table_cell_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("WelcomeScreen", table_cell_style), Paragraph("Landing screen with 'Get Started', 'Sign In', and 'Explore as Guest'.", table_cell_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("ModernSignInScreen", table_cell_style), Paragraph("Student credentials + 8-digit Reg No., Course, and 1-tap Google Sign-In.", table_cell_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("OwnerLoginScreen", table_cell_style), Paragraph("Kitchen staff portal with credentials authentication + 6-digit email OTP.", table_cell_style)],
    [Paragraph("Onboarding & Auth", table_cell_style), Paragraph("ResetPasswordScreen", table_cell_style), Paragraph("Handles deep links (messq://reset-password) for credential updates.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("HomeScreen", table_cell_style), Paragraph("Multi-block canteen selector, live crowd rush badge, quick order categories.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("MenuScreen", table_cell_style), Paragraph("Food catalog with Veg/Non-Veg filter, price/time sorting, and search.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("FoodDetailScreen", table_cell_style), Paragraph("Dish calories, prep minutes, ingredients list, bread/spice options, and addons.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("CartScreen", table_cell_style), Paragraph("Itemized cart breakdown, customized addons, price calculations, and ASAP pickup scheduling.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("CustomerOrderTrackingScreen", table_cell_style), Paragraph("Live 4-step progress stepper, estimated wait countdown, and counter location.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("OrdersScreen", table_cell_style), Paragraph("Split active live orders and past receipts with quick re-order shortcuts.", table_cell_style)],
    [Paragraph("Student Experience", table_cell_style), Paragraph("ProfileScreen", table_cell_style), Paragraph("Student registration details, saved favorite foods, and logout action.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("OwnerDashboardScreen", table_cell_style), Paragraph("Today's revenue (₹), orders count, active queue, and canteen open/close switch.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("OwnerOrdersScreen", table_cell_style), Paragraph("Live kitchen queue with status advance: New -> Preparing -> Ready -> Handover.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("ManageItemsScreen", table_cell_style), Paragraph("Menu editor to create dishes, change prices, and toggle in/out of stock.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("InventoryScreen", table_cell_style), Paragraph("Stock and raw material inventory tracking with low-quantity warnings.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("OwnerCustomersScreen", table_cell_style), Paragraph("Student customer directory with order counts and quick contact access.", table_cell_style)],
    [Paragraph("Owner Operations", table_cell_style), Paragraph("OwnerProfileScreen", table_cell_style), Paragraph("Canteen timings, location info, staff emails, and security settings.", table_cell_style)]
]

t_scr = Table(scr_rows, colWidths=[1.5*inch, 1.8*inch, 4.0*inch])
t_scr.setStyle(TableStyle([
    ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#F3F4F6')),
    ('GRID', (0, 0), (-1, -1), 0.5, colors.HexColor('#E5E7EB')),
    ('TOPPADDING', (0, 0), (-1, -1), 2.5),
    ('BOTTOMPADDING', (0, 0), (-1, -1), 2.5),
]))
story.append(t_scr)
story.append(Spacer(1, 8))

# 6. Architectural Highlights
story.append(Paragraph("6. Architectural Highlights & Key Features", h1_style))
bullets = [
    ("Block-Aware Crowd Status", "Calculates live kitchen rush (Fast Service, Moderate Crowd, High Rush) and wait times in minutes based on active queue volume."),
    ("Contextual Dish Customization", "Supports option selection (Bread type, Roast level, Spice level) paired with paid addons (Extra Cheese, Extra Sauce) updating order totals dynamically."),
    ("Floating Quick-Cart Capsule", "Non-intrusive animated bottom widget showing newly added items with a one-tap shortcut to view cart."),
    ("Resend Two-Factor OTP Security", "Kitchen administration is protected by dispatching 6-digit verification codes to the owner's verified email via the Resend API."),
    ("Canteen Status Broadcast", "Allows owners to pause incoming orders during breaks or kitchen restocking, broadcasting the reason across student screens.")
]
for title, desc in bullets:
    story.append(Paragraph(f"• <b>{title}</b>: {desc}", body_style))

doc.build(story)
print(f"PDF document saved to {pdf_path}")

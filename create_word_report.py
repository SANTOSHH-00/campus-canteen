import os
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

doc = Document()

# Set standard margins
sections = doc.sections
for section in sections:
    section.top_margin = Inches(0.8)
    section.bottom_margin = Inches(0.8)
    section.left_margin = Inches(0.8)
    section.right_margin = Inches(0.8)

# Helper function to set cell background color
def set_cell_background(cell, hex_color):
    tcPr = cell._element.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    tcPr.append(shd)

# Document Title
title_p = doc.add_paragraph()
title_p.paragraph_format.space_before = Pt(0)
title_p.paragraph_format.space_after = Pt(4)
title_run = title_p.add_run("Campus Canteen (MessQ) – Project Report")
title_run.font.name = "Calibri"
title_run.font.size = Pt(26)
title_run.font.bold = True
title_run.font.color.rgb = RGBColor(31, 41, 55)

# Subtitle
sub_p = doc.add_paragraph()
sub_p.paragraph_format.space_after = Pt(18)
sub_run = sub_p.add_run("Comprehensive Technical & Architectural Overview for Stakeholders & Partners")
sub_run.font.name = "Calibri"
sub_run.font.size = Pt(13)
sub_run.font.italic = True
sub_run.font.color.rgb = RGBColor(245, 166, 35)

# 1. Executive Summary
h1 = doc.add_heading(level=1)
h1_run = h1.add_run("1. Executive Summary & Purpose")
h1_run.font.name = "Calibri"
h1_run.font.color.rgb = RGBColor(234, 88, 12)

p_summary = doc.add_paragraph(
    "Campus Canteen (MessQ) is a modern, high-performance mobile application designed specifically to eliminate "
    "cafeteria congestion, long ordering queues, and order errors across college and university campuses. "
    "By interconnecting campus students with multi-block canteen kitchens in real time, students can discover "
    "menus, check live crowd density, place customized orders with dietary preferences, and track progress "
    "digitally. Canteen owners and kitchen operators receive dedicated live order queues, stock and menu controls, "
    "and two-factor security authentication."
)
p_summary.paragraph_format.line_spacing = 1.15
p_summary.paragraph_format.space_after = Pt(12)

# 2. Technology Stack
h2 = doc.add_heading(level=1)
h2_run = h2.add_run("2. Technology Stack & Frameworks")
h2_run.font.name = "Calibri"
h2_run.font.color.rgb = RGBColor(234, 88, 12)

tech_data = [
    ("Component", "Technology Used", "Role & Description"),
    ("Mobile OS & Language", "Android · Kotlin 2.x", "Native client architecture with modern Coroutines & Flow"),
    ("UI Framework", "Jetpack Compose (Material3)", "100% declarative UI with custom Amber & Delivery Orange theme tokens"),
    ("Primary Cloud Database", "Supabase (PostgreSQL)", "Relational data store with Row Level Security (RLS) & Realtime WebSockets"),
    ("Companion Backend", "Firebase Suite", "Firestore offline cache, FCM push notifications & Cloud Storage"),
    ("Authentication", "Supabase Auth + Google Credential Manager", "Email/Password with deep link recovery + 1-Tap Google Sign-In"),
    ("Email OTP Security", "Resend API (REST HTTP)", "Dispatches automated 6-digit branded HTML verification codes to owners"),
    ("Image Loading & Network", "Coil Compose & Retrofit/OkHttp3", "Asynchronous cached network image rendering & REST calls")
]

t_tech = doc.add_table(rows=len(tech_data), cols=3)
t_tech.alignment = WD_TABLE_ALIGNMENT.CENTER
for r_idx, row in enumerate(tech_data):
    for c_idx, val in enumerate(row):
        cell = t_tech.cell(r_idx, c_idx)
        cell.text = val
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(4)
        p.paragraph_format.space_after = Pt(4)
        run = p.runs[0]
        run.font.name = "Calibri"
        if r_idx == 0:
            set_cell_background(cell, "F3F4F6")
            run.font.bold = True
            run.font.size = Pt(10)
        else:
            run.font.size = Pt(9.5)

doc.add_paragraph().paragraph_format.space_after = Pt(12)

# System Architecture Flowchart
h_arch = doc.add_heading(level=2)
h_arch_run = h_arch.add_run("System Architecture Diagram")
h_arch_run.font.name = "Calibri"
h_arch_run.font.color.rgb = RGBColor(31, 41, 55)

if os.path.exists("scratch_diagrams/architecture_diagram.png"):
    p_img = doc.add_paragraph()
    p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_img.add_run().add_picture("scratch_diagrams/architecture_diagram.png", width=Inches(6.2))
    p_caption = doc.add_paragraph()
    p_caption.alignment = WD_ALIGN_PARAGRAPH.CENTER
    c_run = p_caption.add_run("Figure 1: Dual Backend Architecture & Client-Server Connectivity")
    c_run.font.name = "Calibri"
    c_run.font.size = Pt(9)
    c_run.font.italic = True
    c_run.font.color.rgb = RGBColor(107, 114, 128)

# 3. Roles & Permissions
h3 = doc.add_heading(level=1)
h3_run = h3.add_run("3. System Roles & Access Matrix")
h3_run.font.name = "Calibri"
h3_run.font.color.rgb = RGBColor(234, 88, 12)

role_data = [
    ("Feature / Capability", "Student / Customer", "Canteen Owner / Staff", "Guest Visitor"),
    ("Sign-In Method", "Email, Google, Reg. No.", "Owner Email + 6-Digit Resend OTP", "Explore Only"),
    ("Menu & Crowd Rush Status", "Yes (All campus blocks)", "Assigned Canteen Only", "Yes (Read-only)"),
    ("Dish Customization & Addons", "Full Customization", "No (Manages options)", "View Only"),
    ("Order Placement & Token", "Yes (Digital Token #TK)", "No", "Prompted to Sign In"),
    ("Live Tracking (4 Stages)", "Yes (Realtime progress)", "Full Kitchen View", "No"),
    ("Live Order Queue Control", "No", "Yes (Accept, Ready, Handover)", "No"),
    ("Menu Item CRUD", "No", "Yes (Add, Price, Stock Toggle)", "No"),
    ("Inventory Stock Management", "No", "Yes (Counts & Low Stock Alerts)", "No"),
    ("Canteen Status Broadcast", "No", "Yes (Open/Close with Reason)", "No"),
    ("Sales & Revenue Reports", "No", "Yes (Daily Revenue & Volume)", "No")
]

t_role = doc.add_table(rows=len(role_data), cols=4)
t_role.alignment = WD_TABLE_ALIGNMENT.CENTER
for r_idx, row in enumerate(role_data):
    for c_idx, val in enumerate(row):
        cell = t_role.cell(r_idx, c_idx)
        cell.text = val
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(3)
        p.paragraph_format.space_after = Pt(3)
        run = p.runs[0]
        run.font.name = "Calibri"
        if r_idx == 0:
            set_cell_background(cell, "FEF3C7")
            run.font.bold = True
            run.font.size = Pt(9.5)
        else:
            run.font.size = Pt(9)

doc.add_paragraph().paragraph_format.space_after = Pt(12)

# 4. End-to-End Workflow & Flowchart
h4 = doc.add_heading(level=1)
h4_run = h4.add_run("4. End-to-End Operational Workflow")
h4_run.font.name = "Calibri"
h4_run.font.color.rgb = RGBColor(234, 88, 12)

p_flow = doc.add_paragraph(
    "The lifecycle of a meal order follows a strictly coordinated four-step process. "
    "When a student places an order, it is broadcast to the respective canteen's queue. "
    "Kitchen personnel advance the order stages, updating the student's phone instantly via WebSocket push updates."
)
p_flow.paragraph_format.space_after = Pt(8)

if os.path.exists("scratch_diagrams/workflow_diagram.png"):
    p_img2 = doc.add_paragraph()
    p_img2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_img2.add_run().add_picture("scratch_diagrams/workflow_diagram.png", width=Inches(6.2))
    p_caption2 = doc.add_paragraph()
    p_caption2.alignment = WD_ALIGN_PARAGRAPH.CENTER
    c_run2 = p_caption2.add_run("Figure 2: Four-Stage Real-Time Ordering and Kitchen Execution Flow")
    c_run2.font.name = "Calibri"
    c_run2.font.size = Pt(9)
    c_run2.font.italic = True
    c_run2.font.color.rgb = RGBColor(107, 114, 128)

# 5. Screen Inventory (27 Screens)
h5 = doc.add_heading(level=1)
h5_run = h5.add_run("5. Comprehensive Screen Inventory (27 Screens)")
h5_run.font.name = "Calibri"
h5_run.font.color.rgb = RGBColor(234, 88, 12)

screens = [
    ("Onboarding & Auth", "SplashScreen", "Initial startup checking active sessions, authentication status, and internet reachability."),
    ("Onboarding & Auth", "NoInternetScreen", "Offline error interceptor with real-time retry connectivity verification."),
    ("Onboarding & Auth", "WelcomeOnboardingScreen", "Visual landing portal with 'Get Started', 'Sign In', and 'Explore as Guest' paths."),
    ("Onboarding & Auth", "ModernSignInScreen", "Student credentials login & registration (8-digit Reg. No., Course) plus 1-tap Google Sign-In."),
    ("Onboarding & Auth", "ForgotPasswordScreen", "Direct email password recovery dispatcher."),
    ("Onboarding & Auth", "ResetPasswordScreen", "Handles deep links (messq://reset-password) to safely update user credentials."),
    ("Onboarding & Auth", "OwnerLoginScreen", "Kitchen staff authentication requiring credentials followed by 6-digit email OTP."),
    ("Onboarding & Auth", "CanteenAllocationScreen", "First-time owner setup popup to link email accounts to a campus block canteen."),
    ("Student Experience", "HomeScreen", "Multi-block location switcher, live crowd status indicators (Low/Busy), and food categories."),
    ("Student Experience", "MenuScreen", "Full menu browser with dietary filters (Veg/Non-Veg), price/time sorting, and search."),
    ("Student Experience", "FoodDetailScreen", "In-depth meal view with calorie metrics, ingredient breakdown, bread/spice options, and addons."),
    ("Student Experience", "CartScreen", "Itemized cart breakdown, customized addons, price calculations, and ASAP pickup scheduling."),
    ("Student Experience", "OrdersScreen", "Tabbed view of ongoing live orders and completed historical receipts with re-order shortcuts."),
    ("Student Experience", "CustomerOrderTrackingScreen", "Real-time 4-step order tracker with estimated prep countdown and counter location."),
    ("Student Experience", "ProfileScreen", "Student identity details, favorite items, notification settings, and logout action."),
    ("Owner Management", "OwnerDashboardScreen", "Operations control center: Today's revenue (₹), total orders, quick actions, and open/close switch."),
    ("Owner Management", "OwnerOrdersScreen", "Live kitchen queue sorted by status (New, Preparing, Ready, Completed) with quick action buttons."),
    ("Owner Management", "OwnerOrderDetailsScreen", "Granular order breakdown: buyer contact, customized addons, payment state, and kitchen notes."),
    ("Owner Management", "ManageItemsScreen", "Menu catalog manager for creating, editing, re-pricing, and toggling item in-stock status."),
    ("Owner Management", "InventoryScreen", "Stock and raw material tracking with low quantity alerts."),
    ("Owner Management", "OwnerCustomersScreen", "Student customer directory recording order frequency and direct contact channels."),
    ("Owner Management", "OwnerProfileScreen", "Canteen metadata, opening hours, contact details, and security controls.")
]

t_scr = doc.add_table(rows=len(screens)+1, cols=3)
t_scr.alignment = WD_TABLE_ALIGNMENT.CENTER
headers = ["Section", "Screen Name", "Functional Description"]
for c_idx, h_text in enumerate(headers):
    cell = t_scr.cell(0, c_idx)
    cell.text = h_text
    set_cell_background(cell, "F3F4F6")
    p = cell.paragraphs[0]
    run = p.runs[0]
    run.font.name = "Calibri"
    run.font.bold = True
    run.font.size = Pt(9.5)

for r_idx, row in enumerate(screens):
    for c_idx, val in enumerate(row):
        cell = t_scr.cell(r_idx + 1, c_idx)
        cell.text = val
        p = cell.paragraphs[0]
        p.paragraph_format.space_before = Pt(2.5)
        p.paragraph_format.space_after = Pt(2.5)
        run = p.runs[0]
        run.font.name = "Calibri"
        run.font.size = Pt(8.5)

doc.add_paragraph().paragraph_format.space_after = Pt(12)

# 6. Key Innovations
h6 = doc.add_heading(level=1)
h6_run = h6.add_run("6. Key Features & Architectural Innovations")
h6_run.font.name = "Calibri"
h6_run.font.color.rgb = RGBColor(234, 88, 12)

innovations = [
    ("Block-Aware Dynamic Crowd Status", "Calculates live kitchen rush (Fast Service, Moderate Crowd, High Rush) and average wait times in minutes based on active orders."),
    ("Granular Dish Customization", "Contextual options (Bread type, Roast level, Spice level) paired with paid addons (Extra Cheese, Extra Sauce) that recalculate totals in real time."),
    ("Floating Quick-Cart Capsule", "A lightweight, animated floating widget notifying students when items are added and allowing one-tap checkout navigation without cluttering the screen."),
    ("Resend Two-Factor OTP Security", "Kitchen administration is protected by dispatching 6-digit verification codes to the owner's verified email via the Resend API."),
    ("Canteen Status Broadcast", "Allows owners to pause incoming orders during breaks or kitchen restocking, automatically broadcasting the reason across student screens.")
]

for title, desc in innovations:
    p = doc.add_paragraph()
    p.paragraph_format.space_after = Pt(4)
    r_bullet = p.add_run("• " + title + ": ")
    r_bullet.font.name = "Calibri"
    r_bullet.font.bold = True
    r_bullet.font.size = Pt(10)
    r_bullet.font.color.rgb = RGBColor(31, 41, 55)
    
    r_desc = p.add_run(desc)
    r_desc.font.name = "Calibri"
    r_desc.font.size = Pt(9.5)

output_path = "c:/Users/Santosh/Downloads/campus-canteen/Campus_Canteen_MessQ_Project_Report.docx"
doc.save(output_path)
print(f"Word document saved to {output_path}")

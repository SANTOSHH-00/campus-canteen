import os
import matplotlib.pyplot as plt
import matplotlib.patches as patches

os.makedirs("scratch_diagrams", exist_ok=True)

# 1. Architecture Diagram
def create_architecture_diagram():
    fig, ax = plt.subplots(figsize=(10, 5), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 5.5)
    ax.axis('off')

    # Title
    ax.text(5, 5.2, "Campus Canteen (MessQ) - System Architecture", ha='center', va='center', fontsize=14, fontweight='bold', color='#1F2937')

    # Student Layer
    rect_student = patches.FancyBboxPatch((0.5, 2.8), 2.6, 1.8, boxstyle="round,pad=0.1", fc="#FEF3C7", ec="#F5A623", lw=2)
    ax.add_patch(rect_student)
    ax.text(1.8, 4.2, "Student / Customer", ha='center', va='center', fontsize=11, fontweight='bold', color='#92400E')
    ax.text(1.8, 3.7, "• Jetpack Compose UI\n• Multi-Block Menu\n• Custom Cart & Addons\n• Real-Time Order Tracking", ha='center', va='center', fontsize=8.5, color='#1F2937')

    # Owner Layer
    rect_owner = patches.FancyBboxPatch((6.9, 2.8), 2.6, 1.8, boxstyle="round,pad=0.1", fc="#FFEDD5", ec="#EA580C", lw=2)
    ax.add_patch(rect_owner)
    ax.text(8.2, 4.2, "Canteen Owner / Staff", ha='center', va='center', fontsize=11, fontweight='bold', color='#9A3412')
    ax.text(8.2, 3.7, "• Kitchen Live Queue\n• 6-Digit Email OTP (2FA)\n• Item & Inventory CRUD\n• Open/Close Status Toggle", ha='center', va='center', fontsize=8.5, color='#1F2937')

    # Backend Layer (Center)
    rect_cloud = patches.FancyBboxPatch((3.7, 0.5), 2.6, 3.8, boxstyle="round,pad=0.1", fc="#EFF6FF", ec="#2563EB", lw=2)
    ax.add_patch(rect_cloud)
    ax.text(5.0, 4.0, "Dual Cloud Backend", ha='center', va='center', fontsize=11, fontweight='bold', color='#1E40AF')
    
    # Sub boxes inside Cloud
    ax.text(5.0, 3.2, "Supabase (Primary SQL)\n- PostgreSQL Database\n- Realtime WebSocket\n- Storage Buckets", ha='center', va='center', fontsize=8.5, bbox=dict(boxstyle="round,pad=0.3", fc="#DBEAFE", ec="#3B82F6", lw=1))
    ax.text(5.0, 1.9, "Firebase Suite\n- Firestore Offline Cache\n- Google Credentials Auth\n- Cloud Messaging (FCM)", ha='center', va='center', fontsize=8.5, bbox=dict(boxstyle="round,pad=0.3", fc="#DBEAFE", ec="#3B82F6", lw=1))
    ax.text(5.0, 0.9, "Resend Email Service\n- Dynamic 6-Digit Security OTP", ha='center', va='center', fontsize=8.5, bbox=dict(boxstyle="round,pad=0.3", fc="#FEF9C3", ec="#CA8A04", lw=1))

    # Connectors
    ax.annotate("", xy=(3.6, 3.7), xytext=(3.15, 3.7), arrowprops=dict(arrowstyle="<->", color="#4B5563", lw=2))
    ax.annotate("", xy=(6.85, 3.7), xytext=(6.35, 3.7), arrowprops=dict(arrowstyle="<->", color="#4B5563", lw=2))
    
    plt.tight_layout()
    plt.savefig("scratch_diagrams/architecture_diagram.png", bbox_inches='tight')
    plt.close()

# 2. Workflow Diagram
def create_workflow_diagram():
    fig, ax = plt.subplots(figsize=(10, 4.5), dpi=300)
    ax.set_xlim(0, 10)
    ax.set_ylim(0, 4.5)
    ax.axis('off')

    ax.text(5, 4.2, "End-to-End Order & Kitchen Workflow", ha='center', va='center', fontsize=14, fontweight='bold', color='#1F2937')

    steps = [
        ("1. Browse & Customize", "Select Canteen\nPick items & addons\nCheck wait time", "#FEF3C7", "#D97706"),
        ("2. Order Placement", "Review bill\nSelect ASAP pickup\nGenerate Token #TK", "#DBEAFE", "#2563EB"),
        ("3. Kitchen Queue", "Owner accepts order\nTaps 'Start Preparing'\nLive time countdown", "#FFEDD5", "#EA580C"),
        ("4. Pickup & Handover", "Order marked 'Ready'\nStudent gets alert\nToken verification", "#DCFCE7", "#16A34A")
    ]

    for i, (title, desc, bg, border) in enumerate(steps):
        x = 0.5 + i * 2.4
        rect = patches.FancyBboxPatch((x, 1.2), 2.0, 2.2, boxstyle="round,pad=0.1", fc=bg, ec=border, lw=2)
        ax.add_patch(rect)
        ax.text(x + 1.0, 3.0, title, ha='center', va='center', fontsize=9.5, fontweight='bold', color=border)
        ax.text(x + 1.0, 2.0, desc, ha='center', va='center', fontsize=8.5, color='#1F2937', multialignment='center')

        if i < 3:
            ax.annotate("", xy=(x + 2.35, 2.3), xytext=(x + 2.05, 2.3), arrowprops=dict(arrowstyle="->", color="#4B5563", lw=2.5))

    ax.text(5, 0.4, "Real-time sync between Student and Owner views powered by Supabase & Firebase", ha='center', va='center', fontsize=9, style='italic', color='#6B7280')

    plt.tight_layout()
    plt.savefig("scratch_diagrams/workflow_diagram.png", bbox_inches='tight')
    plt.close()

if __name__ == '__main__':
    create_architecture_diagram()
    create_workflow_diagram()
    print("Diagrams generated successfully.")

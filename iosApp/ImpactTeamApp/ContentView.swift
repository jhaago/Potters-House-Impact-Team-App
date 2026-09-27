import ImpactTeamShared
import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(spacing: 12) {
            Text(AppIdentityKt.defaultApplicationName())
                .font(.title)
                .multilineTextAlignment(.center)
            Text("Tracking proof not started")
                .font(.body)
        }
        .padding(24)
    }
}

#Preview {
    ContentView()
}

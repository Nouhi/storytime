import SwiftUI

struct PageNavigationOverlay: View {
    @Binding var currentPage: Int
    let totalPages: Int

    var body: some View {
        HStack {
            // Left arrow
            Button {
                withAnimation {
                    currentPage = max(1, currentPage - 1)
                }
            } label: {
                Image(systemName: "chevron.left")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .background(.black.opacity(0.35))
                    .clipShape(Circle())
            }
            .opacity(currentPage > 1 ? 1 : 0)
            .padding(.leading, 8)

            Spacer()

            // Right arrow
            Button {
                withAnimation {
                    currentPage = min(totalPages, currentPage + 1)
                }
            } label: {
                Image(systemName: "chevron.right")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .frame(width: 36, height: 36)
                    .background(.black.opacity(0.35))
                    .clipShape(Circle())
            }
            .opacity(currentPage < totalPages ? 1 : 0)
            .padding(.trailing, 8)
        }
    }
}
